var {List, Map, Record} = require('immutable');

var p = require('./parser');
var e = require('./expr');
const {NSEnv, NSHeader, Var, makeSafe, sym} = require('./env');
var f = require('./form');
var lv = require('./localVar');

function nsSymParser(ns, brjFile) {
  return symForm => {
    if (ns === undefined || symForm.sym.name === ns) {
      return p.pure(p.successResult(new NSHeader({ns: symForm.sym.name, brjFile})));
    } else {
      return p.pure(p.failResult(`Unexpected NS, expecting '${ns}', got '${symForm.sym}'`));
    }
  };
}

const refersParser = p.RecordParser.then(
  refers => p.innerFormsParser(refers.entries, p.atLeastOneOf(p.oneOf(
    referEntry => p.parseForms(List.of(referEntry.key, referEntry.value), p.SymbolNameParser.then(
      referredNS => p.VectorParser.then(
        referredSymForms => p.innerFormsParser(
          referredSymForms.forms,
          p.atLeastOneOf(p.SymbolNameParser))).fmap(
            referredSyms => ({referredNS, referredSyms}))))))).then(
              parsedRefers => {
                parsedRefers = parsedRefers.toArray();
                const referredNSs = [];
                const referredSymsMap = {};

                for (let {referredNS, referredSyms} of parsedRefers) {
                  referredSyms = referredSyms.toArray();

                  if (referredNSs.indexOf(referredNS) != -1) {
                    return p.pure(p.failResult(`Duplicate refer for NS '${referredNS}'`));
                  }

                  for (const referredSym of referredSyms) {
                    if (referredSymsMap[referredSym] !== undefined) {
                      return p.pure(p.failResult(`Duplicate refer for symbol ${referredSym}`));
                    }

                    referredSymsMap[referredSym] = referredNS;
                  }
                }

                return p.pure(p.successResult(Map(referredSymsMap)));
              }));


const aliasesParser = p.RecordParser.then(
  aliases => p.innerFormsParser(aliases.entries, p.atLeastOneOf(p.oneOf(
    aliasEntry => p.parseForms(List.of(aliasEntry.key, aliasEntry.value), p.SymbolNameParser.then(
      alias => p.SymbolNameParser.fmap(
        aliasedNS => ({alias, aliasedNS}))))))).then(
          parsedAliases => {
            parsedAliases = parsedAliases.toArray();
            const aliasedNSs = [];
            const aliasedNSsMap = {};

            for (const {alias, aliasedNS} of parsedAliases) {
              if (aliasedNSs.indexOf(aliasedNS) != -1) {
                return p.pure(p.failResult(`Duplicate alias for NS '${aliasedNS}'`));
              }

              aliasedNSs.push(aliasedNS);
              aliasedNSsMap[alias] = aliasedNS;
            }

            return p.pure(p.successResult(Map(aliasedNSsMap)));
          }));

function nsOptsParser(nsHeader) {
  return recordForm => p.innerFormsParser(recordForm.entries, p.atLeastOneOf(p.oneOf(
    entry => p.parseForms(List.of(entry.key, entry.value), p.SymbolNameParser.then(
      entryType => {
        switch(entryType) {
        case 'refers':
          return refersParser.fmap(refers => ({type: 'refers', value: refers}));
        case 'aliases':
          return aliasesParser.fmap(aliases => ({type: 'aliases', value: aliases}));

        default:
          return p.pure(p.failResult(`Unexpected key '${entryType}' in NS declaration`));
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

function readNSHeader(ns, brjFile, form) {
  return p.parseForm(form, p.ListParser.then(listForm => {
    return p.innerFormsParser(
      listForm.forms,
      p.isSymbol(sym('ns')).then(
        _ => p.SymbolParser.then(nsSymParser(ns, brjFile)))
        .then(nsHeader => p.anyOf(p.parseEnd(nsHeader), p.RecordParser.then(nsOptsParser(nsHeader)))));
  })).orThrow();
}

function resolveNSHeader(env, header) {
  const refers = header.refers.mapEntries(([sym, referredNS]) => {
    const referredNSEnv = env.nsEnvs.get(referredNS);
    if (referredNSEnv === undefined) {
      throw `Can't find referred NS '${referredNS}' from ${header.ns}`;
    }

    const referExport = referredNSEnv.exports.get(sym);
    if (referExport === undefined) {
      throw `Can't find refer: '${referredNS}/${sym}' from '${header.ns}'`;
    }

    return [sym, referExport];
  });

  const aliases = header.aliases.mapEntries(([sym, aliasedNS]) => {
    const aliasedNSEnv = env.nsEnvs.get(aliasedNS);
    if (aliasedNSEnv === undefined) {
      throw `Can't find aliased NS '${aliasedNS}' from ${header.ns}`;
    }

    return [sym, aliasedNSEnv];
  });

  return new NSEnv({ns: header.ns, brjFile: header.brjFile, nsHash: header.nsHash, refers, aliases});
}

function analyseForm(env, nsEnv, form) {
  function analyseValueExpr(form, opts) {
    const {localEnv, loopLVs, isTail} = opts;

    function exprParser({localEnv = opts.localEnv, loopLVs = opts.loopLVs, isTail = opts.isTail}) {
      return p.oneOf(form => p.successResult(analyseValueExpr(form, {localEnv, loopLVs, isTail})));
    };

    const range = form.range;
    const optsNoTail = {localEnv, isTail: false};

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
      return new e.VectorExpr({range, exprs: form.forms.map(f => analyseValueExpr(f, optsNoTail))});
    case 'set':
      return new e.SetExpr({range, exprs: form.forms.map(f => analyseValueExpr(f, optsNoTail))});

    case 'record':
      return new e.RecordExpr({
        range,
        entries: form.entries.map(
          entry => new e.RecordEntry({
            key: p.parseForm(entry.key, p.SymbolParser.fmap(symForm => symForm.sym)).orThrow(),
            value: analyseValueExpr(entry.value, optsNoTail)}))});

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
            return p.parseForms(forms.shift(), exprParser({}).then(
              testExpr => exprParser({}).then(
                thenExpr => exprParser({}).then(
                  elseExpr => p.parseEnd(new e.IfExpr({range, testExpr, thenExpr, elseExpr})))))).orThrow();

          case 'let':
            return p.parseForms(forms.shift(), p.VectorParser.then(
              bindingVecForm => {
                const bindingVecForms = bindingVecForm.forms;
                if (bindingVecForms.size % 2 !== 0) {
                  return p.pure(p.failResult('let binding must have an even number of forms'));
                } else {
                  return p.innerFormsParser(
                    bindingVecForms,
                    p.atLeastOneOf(p.SymbolNameParser.then(
                      param => exprParser({isTail: false}).fmap(
                        expr => new e.BindingPair({
                          localVar: lv(param),
                          expr
                        }))
                    )).then(p.parseEnd));
                }}).then(
                  bindings => exprParser({
                    localEnv: bindings.reduce((localEnv, binding) => localEnv.set(binding.localVar.name, binding.localVar), localEnv)
                  }).then(
                    body => p.parseEnd(new e.LetExpr({range, bindings, body})))))
              .orThrow();

          case 'loop': {
            return p.parseForms(forms.shift(), p.VectorParser.then(
              bindingsForm => {

                if (bindingsForm.forms.size % 2 == 0) {
                  return p.innerFormsParser(
                    bindingsForm.forms,
                    p.atLeastOneOf(p.SymbolNameParser.then(
                      param => exprParser({isTail: false}).fmap(
                        initialBindingExpr => new e.BindingPair({
                          localVar: lv(param),
                          expr: initialBindingExpr
                        })))).then(p.parseEnd));
                } else {
                  return p.pure(p.failResult('loop binding must have an even number of forms'));
                }
              }).then(
                bindings => {
                  return p.oneOf(form => p.successResult(analyseValueExpr(form, {
                    localEnv: bindings.reduce(
                      (localEnv, binding) => localEnv.set(binding.localVar.name, binding.localVar), localEnv),
                    loopLVs: bindings.map(b => b.localVar),
                    isTail: true
                  }))).then(
                    body => p.parseEnd(new e.LoopExpr({range: form.range, bindings, body})));
                })).orThrow();
          }

          case 'recur':
            if (!loopLVs) {
              throw 'recur not in loop';
            } else if (!isTail) {
              throw 'recur not in tail position';
            } else if (forms.shift().size != loopLVs.size) {
              throw `wrong number of forms in recur - got ${forms.shift().size}, expecting ${loopLVs.size}`;
            } else {
              return p.parseForms(forms.shift(), p.atLeastOneOf(exprParser({isTail: false, loopLVs: null})).then(
                recurExprs => p.parseEnd(new e.RecurExpr({
                  range: form.range,
                  bindings: loopLVs.zip(recurExprs).map(([localVar, expr]) => e.BindingPair({localVar, expr}))
                })))).orThrow();
            }

          case 'fn':
            return p.parseForms(forms.shift(), p.ListParser.then(
              paramsForm => p.innerFormsParser(
                paramsForm.forms,
                p.atLeastOneOf(p.SymbolParser.fmap(symForm => symForm.sym))).then(
                  paramSyms => {
                    const params = paramSyms.map(sym => lv(sym.name));
                    const localEnv_ = localEnv.merge(Map(params.map(param => [param.name, param])));

                    return p.oneOf(form => p.successResult(analyseValueExpr(form, {localEnv: localEnv_})))
                      .then(body => p.parseEnd(new e.FnExpr({range, params, body})));
                  }))).orThrow();

          case 'match':
            return p.parseForms(forms.shift(), exprParser({isTail: false}).then(
              matchExpr => p.atLeastOneOf(p.anyOf(
                p.SymbolNameParser.then(
                  dataTypeName => {
                    const dataType = nsEnv.exports.get(dataTypeName);
                    if (dataType) {
                      return p.pure(p.successResult({dataType}));
                    } else {
                      const dataType = nsEnv.refers.get(dataTypeName);
                      if (dataType) {
                        return p.pure(p.successResult({dataType}));
                      }

                      return p.pure(p.failResult(`can't find data type '${dataTypeName}'`));
                    }
                  }),
                p.NamespacedSymbolParser.then(
                  nsSymForm => {
                    const aliasNSEnv = nsEnv.aliases.get(nsSymForm.sym.ns);
                    if (aliasNSEnv) {
                      const dataType = aliasNSEnv.exports.get(nsSymForm.sym.name);
                      if (dataType) {
                        return p.pure(p.successResult({dataType, alias: nsSymForm.sym.ns}));
                      }
                    }

                    return p.pure(p.failResult(`can't find data type '${nsSymForm.sym.ns}/${nsSymForm.sym.name}'`));
                  })).then(
                    ({dataType, alias}) => exprParser({}).then(
                      expr => p.pure(p.successResult(new e.MatchClause({var: dataType, alias, expr}))))))

                .then(clauses => p.parseEnd(new e.MatchExpr({range: form.range, expr: matchExpr, clauses}))))).orThrow();

          default:
            return new e.CallExpr({range, exprs: form.forms.map(form => analyseValueExpr(form, {localEnv}))});
          }

        case 'namespacedSymbol':
          // fall through - this will then call through to the top level namespacedSymbol handling
        case 'list':
          return new e.CallExpr({range, exprs: form.forms.map(form => analyseValueExpr(form, {localEnv}))});

        default:
          throw `unexpected form type ${firstForm.formType}`;
        }
      }

    case 'symbol':
      const sym = form.sym;

      const localVar = localEnv.get(sym.name);
      if (localVar !== undefined) {
        return new e.LocalVarExpr({range, localVar, name: sym.name});
      }

      const globalVar = nsEnv.exports.get(sym.name) || nsEnv.refers.get(sym.name);
      if (globalVar) {
        return new e.VarExpr({range, var: globalVar});
      }

      throw `NIY - can't find '${form.sym}'`;

    case 'namespacedSymbol':
      if (form.sym.ns === 'js') {
        return new e.JSGlobalExpr({range, path: List(form.sym.name.split('.'))});
      } else {
        const aliasedNS = nsEnv.aliases.get(form.sym.ns);

        if (aliasedNS === undefined) {
          throw `No such alias '${form.sym.ns} in NS '${nsEnv.ns}'`;
        }

        const aliasedVar = aliasedNS.exports.get(form.sym.name);

        if (aliasedVar === undefined) {
          throw `No such var '${aliasedNS.ns}/${form.sym.name}', referenced from '${nsEnv.ns}'`;
        }

        return new e.VarExpr({range, var: aliasedVar, alias: form.sym.ns});
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
            p.ListParser.then(paramsForm => p.innerFormsParser(
              paramsForm.forms,
              p.SymbolParser.then(
                nameSymForm => p.atLeastOneOf(p.SymbolNameParser).then(
                  params => p.parseEnd({
                    params: params.map(lv),
                    sym: nameSymForm.sym
                  })))))).then(
              ({params, sym}) => p.oneOf(
                form => {
                  const fnEnv = params ? Map(params.map(p => [p.name, p])): Map({});
                  nsEnv = nsEnv.setIn(['exports', sym.name], new Var({ns: nsEnv.ns, name: sym.name, safeName: makeSafe(sym.name)}));
                  return p.successResult(analyseValueExpr(form, {localEnv: fnEnv, loopLVs: null, isTail: true}));
              }).then(
                body => p.parseEnd(new e.DefExpr({range: form.range, sym, params, body})))))
          .orThrow();

      case 'defdata':
        return p.parseForms(
          forms.shift(),
          p.anyOf(
            p.SymbolNameParser.then(
              name => p.parseEnd(new e.DefDataExpr({range: form.range, type: 'value', name}))),

            p.ListParser.then(
              listForm => p.innerFormsParser(listForm.forms, p.SymbolNameParser.then(
                name => p.anyOf(
                  p.SetParser.then(keySet => p.innerFormsParser(keySet.forms, p.atLeastOneOf(p.SymbolNameParser).then(
                    keys => p.parseEnd(new e.DefDataExpr({range: form.range, name, type: 'record', keys}))))),
                  p.atLeastOneOf(p.SymbolNameParser).then(
                    params => p.parseEnd(new e.DefDataExpr({range: form.range, name, type: 'vector', params}))))))))).orThrow();

      case '::':
        throw 'NIY';

      default:
        return analyseValueExpr(form, {localEnv: Map(), isTail: true});
      }
    }

  }

  return analyseValueExpr(form, {localEnv: Map()});
};

module.exports = {readNSHeader, resolveNSHeader, analyseForm, NSHeader};
