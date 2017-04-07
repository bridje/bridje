var {List, Map, Record} = require('immutable');

var p = require('./parser');
var e = require('./expr');
const {NSEnv, sym} = require('./runtime');
var f = require('./form');
var lv = require('./localVar');

const NSHeader = Record({ns: null, refers: Map({}), aliases: Map({})});

function nsSymParser(ns) {
  return symForm => {
    if (symForm.sym.name === ns) {
      return p.pure(p.successResult(new NSEnv({ns})));
    } else {
      return p.pure(p.failResult(`Unexpected NS, expecting '${ns}', got '${symForm.sym}'`));
    }
  };
}

function nsSymParser_(ns) {
  return symForm => {
    if (symForm.sym.name === ns) {
      return p.pure(p.successResult(new NSHeader({ns})));
    } else {
      return p.pure(p.failResult(`Unexpected NS, expecting '${ns}', got '${symForm.sym}'`));
    }
  };
}

function refersParser(env, nsEnv) {
  return p.RecordParser.then(
    refers => p.innerFormsParser(refers.entries, p.atLeastOneOf(p.oneOf(
      referEntry => p.parseForms(List.of(referEntry.key, referEntry.value), p.SymbolParser.then(
        nsSymForm => {
          const referredNS = nsSymForm.sym.name;
          const referredNSEnv = env.nsEnvs.get(referredNS);
          if (referredNSEnv === undefined) {
            return p.pure(p.failResult(`Unknown NS '${referredNS}'`));
          }
          return p.VectorParser.then(
            referredSyms => p.innerFormsParser(referredSyms.forms, p.atLeastOneOf(p.SymbolParser.then(
              referredSymForm => {
                const referredSym = referredSymForm.sym.name;
                const referredExport = referredNSEnv.exports.get(referredSym);

                if (referredExport === undefined) {
                  return p.pure(p.failResult(`Unknown refer: '${referredNS}/${referredSym}'`));
                } else {
                  return p.pure(p.successResult({referredSym, referredExport}));
                }
              })))).fmap(nsRefers => ({referredNS, nsRefers}));
        }))))).then(
          parsedRefers => {
            parsedRefers = parsedRefers.toArray();
            const referredNSs = [];
            const referredSyms = {};

            for (let i = 0; i < parsedRefers.length; i++) {
              let {referredNS, nsRefers} = parsedRefers[i];
              nsRefers = nsRefers.toArray();
              if (referredNSs.indexOf(referredNS) != -1) {
                return p.pure(p.failResult(`Duplicate refer for NS '${referredNS}'`));
              }

              referredNSs.push(referredNS);
              // TODO check for cycles

              for (let j = 0; j < nsRefers.length; j++) {
                const {referredSym, referredExport} = nsRefers[i];

                if (referredSyms[referredSym] !== undefined) {
                  return p.pure(p.failResult(`Duplicate refer for symbol ${referredSym}`));
                }

                referredSyms[referredSym] = referredExport;
              }
            }

            return p.pure(p.successResult(Map(referredSyms)));
          }));
}

const refersParser_ = p.RecordParser.then(
  refers => p.innerFormsParser(refers.entries, p.atLeastOneOf(p.oneOf(
    referEntry => p.parseForms(List.of(referEntry.key, referEntry.value), p.SymbolParser.then(
      nsSymForm => p.VectorParser.then(
        referredSymForms => p.innerFormsParser(
          referredSymForms.forms,
          p.atLeastOneOf(p.SymbolParser.fmap(referredSymForm => referredSymForm.sym.name)))).fmap(
            referredSyms => ({referredNS: nsSymForm.sym.name, referredSyms}))))))).then(
              parsedRefers => {
                parsedRefers = parsedRefers.toArray();
                const referredNSs = [];
                const referredSymsMap = {};

                for (let i = 0; i < parsedRefers.length; i++) {
                  let {referredNS, referredSyms} = parsedRefers[i];
                  if (referredNSs.indexOf(referredNS) != -1) {
                    return p.pure(p.failResult(`Duplicate refer for NS '${referredNS}'`));
                  }

                  referredNSs.push(referredNS);

                  for (let j = 0; j < referredSyms.length; j++) {
                    const {referredSym, referredExport} = referredSyms[i];

                    if (referredSymsMap[referredSym] !== undefined) {
                      return p.pure(p.failResult(`Duplicate refer for symbol ${referredSym}`));
                    }

                    referredSyms[referredSym] = referredExport;
                  }
                }

                return p.pure(p.successResult(Map(referredSymsMap)));
              }));


function nsOptsParser(env, nsEnv) {
  return recordForm => p.innerFormsParser(recordForm.entries, p.atLeastOneOf(p.oneOf(
    entry => p.parseForms(List.of(entry.key, entry.value), p.SymbolParser.then(
      symForm => {
        switch(symForm.sym.name) {
        case 'refers':
          return refersParser(env, nsEnv).fmap(refers => ({type: 'refers', value: refers}));

        default:
          return p.pure(p.failResult(`Unexpected key '${symForm.sym.name}' in NS declaration`));
        }
      })))).then(
        nsOpts => {
          nsOpts = nsOpts.toArray();

          for (let i = 0; i < nsOpts.length; i++) {
            const {type, value} = nsOpts[i];

            if (!nsEnv.get(type).isEmpty()) {
              return p.pure(p.failResult(`Duplicate '${type}' entry in NS declaration`));
            } else {
              nsEnv = nsEnv.set(type, value);
            }
          }

          return p.pure(p.successResult(nsEnv));
        }));
}


function nsOptsParser_(nsHeader) {
  return recordForm => p.innerFormsParser(recordForm.entries, p.atLeastOneOf(p.oneOf(
    entry => p.parseForms(List.of(entry.key, entry.value), p.SymbolParser.then(
      symForm => {
        switch(symForm.sym.name) {
        case 'refers':
          return refersParser_.fmap(refers => ({type: 'refers', value: refers}));

        default:
          return p.pure(p.failResult(`Unexpected key '${symForm.sym.name}' in NS declaration`));
        }
      })))).then(
        nsOpts => {
          nsOpts = nsOpts.toArray();

          for (let i = 0; i < nsOpts.length; i++) {
            const {type, value} = nsOpts[i];

            if (!nsHeader.get(type).isEmpty()) {
              return p.pure(p.failResult(`Duplicate '${type}' entry in NS declaration`));
            } else {
              nsHeader = nsHeader.set(type, value);
            }
          }

          return p.pure(p.successResult(nsHeader));
        }));
}

function analyseNSForm(env, ns, form) {
  return p.parseForm(form, p.ListParser.then(listForm => {
    return p.innerFormsParser(
      listForm.forms,
      p.isSymbol(sym('ns')).then(
        _ => p.SymbolParser.then(nsSymParser(ns)))
        .then(nsEnv => p.anyOf(p.parseEnd(nsEnv), p.RecordParser.then(nsOptsParser(env, nsEnv)))));
  })).orThrow();
}

function readNSHeader(ns, form) {
  return p.parseForm(form, p.ListParser.then(listForm => {
    return p.innerFormsParser(
      listForm.forms,
      p.isSymbol(sym('ns')).then(
        _ => p.SymbolParser.then(nsSymParser_(ns)))
        .then(nsHeader => p.anyOf(p.parseEnd(nsHeader), p.RecordParser.then(nsOptsParser_(nsHeader)))));
  })).orThrow();
}

function resolveNSHeader(env, header) {
  throw 'niy';
}

function analyseForm(env, nsEnv, form) {
  function analyseValueExpr(localEnv, form) {
    const exprParser = p.oneOf(form => p.successResult(analyseValueExpr(localEnv, form)));
    const range = form.range;

    switch(form.formType) {
    case 'bool':
      return new e.BoolExpr({range, bool: form.bool});
    case 'string':
      return new e.StringExpr({range, str: form.str});
    case 'int':
      return new e.IntExpr({range, int: form.int});
    case 'float':
      return new e.FloatExpr({range, float: form.float});

    case 'vector':
      return new e.VectorExpr({range, exprs: form.forms.map(f => analyseValueExpr(localEnv, f))});
    case 'set':
      return new e.SetExpr({range, exprs: form.forms.map(f => analyseValueExpr(localEnv, f))});

    case 'record':
      return new e.RecordExpr({
        range,
        entries: form.entries.map(
          entry => new e.RecordEntry({
            key: p.parseForm(entry.key, p.SymbolParser.fmap(symForm => symForm.sym)).orThrow(),
            value: analyseValueExpr(localEnv, entry.value)}))});

    case 'list':
      const forms = form.forms;

      if (forms.isEmpty()) {

        throw 'NIY';
      } else {
        const firstForm = forms.first();

        switch (firstForm.formType) {
        case 'symbol':
          switch (firstForm.sym.name) {
          case 'if':
            return p.parseForms(forms.shift(), exprParser.then(
              testExpr => exprParser.then(
                thenExpr => exprParser.then(
                  elseExpr => p.parseEnd(new e.IfExpr({range, testExpr, thenExpr, elseExpr})))))).orThrow();

          case 'let':
            return p.parseForms(forms.shift(), p.VectorParser.fmap(
              bindingVecForm => {
                const bindingVecForms = bindingVecForm.forms;
                if (bindingVecForms.size % 2 !== 0) {
                  return p.pure(p.failResult('let binding must have an even number of forms'));
                } else {
                  let _localEnv = localEnv;
                  let bindings = List();

                  for (let i = 0; i < bindingVecForms.size; i += 2) {
                    let bindingExpr = analyseValueExpr(localEnv, bindingVecForms.get(i + 1));
                    let name = bindingVecForms.get(i).sym.name;
                    let localVar = lv(name);
                    bindings = bindings.push(new e.LetBinding({localVar, expr: bindingExpr}));
                    _localEnv = _localEnv.set(name, localVar);
                  }

                  return {bindings, localEnv: _localEnv};
                }}).then(
                  ({bindings, localEnv}) => p.oneOf(
                    bodyForm => p.successResult({bindings, body: analyseValueExpr(localEnv, bodyForm)})).then(
                      ({bindings, body}) => p.parseEnd(new e.LetExpr({range, bindings, body})))))
              .orThrow();

          case 'fn':
            return p.parseForms(forms.shift(), p.ListParser.then(
              paramsForm => p.innerFormsParser(
                paramsForm.forms,
                p.atLeastOneOf(p.SymbolParser.fmap(symForm => symForm.sym))).then(
                  paramSyms => {
                    const params = paramSyms.map(sym => lv(sym.name));
                    const localEnv_ = localEnv.merge(Map(params.map(param => [param.name, param])));

                    return p.oneOf(form => p.successResult(analyseValueExpr(localEnv_, form)))
                      .then(body => p.parseEnd(new e.FnExpr({range, params, body})));
                  }))).orThrow();

          case 'case':
          case '::':
            throw 'NIY';

          default:
            return new e.CallExpr({range, exprs: form.forms.map(form => analyseValueExpr(localEnv, form))});
          }

        case 'namespacedSymbol':
        case 'list':
          return new e.CallExpr({range, exprs: form.forms.map(form => analyseValueExpr(localEnv, form))});

        default:
          throw `unexpected form type ${firstForm.formType}`;
        }
      }

    case 'symbol':
      const sym = form.sym;

      const localVar = localEnv.get(sym.name);
      const globalVar = localVar === undefined ? nsEnv.exports.get(sym.name) || nsEnv.refers.get(sym.name) : undefined;

      if (localVar !== undefined) {
        return new e.LocalVarExpr({range, localVar, name: sym.name});
      } else if (globalVar !== undefined){
        return new e.VarExpr({range, var: globalVar});
      } else {
        throw "NIY - can't find";
      }

    case 'namespacedSymbol':
      if (form.sym.ns === 'js') {
        return new e.JSGlobalExpr({range, path: List(form.sym.name.split('.'))});
      } else {
        throw 'nsSym niy';
      }

    default:
      throw 'unknown form?';
    }
  };

  if (form.formType == 'list') {
    const forms = form.forms;
    const firstForm = forms.first();
    if (firstForm.formType == 'symbol') {
      switch (firstForm.sym.name) {
      case 'def':
        return p.parseForms(
          forms.shift(),
          p.anyOf(
            p.SymbolParser.fmap(symForm => ({params: null, sym: symForm.sym})),
            p.ListParser.then(paramsForm => {
              return p.innerFormsParser(
                paramsForm.forms,
                p.SymbolParser.then(
                  nameSymForm => p.atLeastOneOf(p.SymbolParser).then(
                    paramSymForms => p.parseEnd({params: paramSymForms.map(psf => lv(psf.sym.name)), sym: nameSymForm.sym}))));
            })).then(
              ({params, sym}) => p.oneOf(
                form => {
                  const fnEnv = params ? Map(params.map(p => [p.name, p])): Map({});
                  return p.successResult(analyseValueExpr(fnEnv, form));
              }).then(
                body => p.parseEnd(new e.DefExpr({range: form.range, sym, params, body})))))
          .orThrow();

      case '::':
        throw 'NIY';

      default:
        return analyseValueExpr(Map(), form);
      }
    }

  }

  return analyseValueExpr(Map(), form);
};

module.exports = {analyseNSForm, readNSHeader, resolveNSHeader, analyseForm, NSHeader};
