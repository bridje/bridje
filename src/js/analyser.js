var {List, Map} = require('immutable');

var p = require('./parser');
var e = require('./expr');
const {NSEnv, sym} = require('./runtime');
var f = require('./form');
var lv = require('./localVar');

function analyseNSForm(env, ns, form) {
  return p.parseForm(form, p.ListParser.then(listForm => {
    return p.innerFormsParser(
      listForm.forms,
      p.isSymbol(sym('ns')).then(
        _ => p.SymbolParser.then(
          symForm => {
            if (symForm.sym.name === ns) {
              return p.pure(p.successResult(new NSEnv({ns})));
            } else {
              return p.pure(p.failResult(`Unexpected NS, expecting '${ns}', got '${symForm.sym}'`));
            }
          })).then(p.parseEnd));
  })).orThrow();
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
            throw 'call niy';
          }

        case 'namespacedSymbol':
          throw 'nsSym niy';

        case 'list':
          return new e.CallExpr({range, exprs: form.forms.map(form => analyseValueExpr(localEnv, form))});

        default:
          throw `unexpected form type ${firstForm.formType}`;
        }
      }

    case 'symbol':
      const sym = form.sym;

      const localVar = localEnv.get(sym.name);
      const nsEnvVar = localVar === undefined ? nsEnv.exports.get(sym.name) : undefined;

      if (localVar !== undefined) {
        return new e.LocalVarExpr({range, localVar, name: sym.name});
      } else if (nsEnvVar !== undefined){
        return new e.VarExpr({range, ns: null, var: nsEnvVar});
      } else {
        throw "NIY - can't find";
      }

    case 'namespacedSymbol':
      throw 'nsSym niy';

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

module.exports = {analyseNSForm, analyseForm};
