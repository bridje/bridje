var im = require('immutable');
var p = require('./parser');
var e = require('./expr');
var f = require('./form');
var Symbol = require('./symbol');

function analyseNSForm(env, ns, form) {
  return p.parseForm(form, p.ListParser.then(listForm => {
    return p.innerFormsParser(listForm.forms, p.isSymbol(Symbol.sym('ns')), p.pure);
  }));
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
        if (firstForm.formType == 'symbol' && firstForm.sym.ns === null) {
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
                  let bindings = im.List();

                  for (let i = 0; i < bindingVecForms.size; i += 2) {
                    console.log(bindingVecForms.get(i), bindingVecForms.get(i+1));

                  }

                  return {bindings, _localEnv};
                }}).then(
                  ({bindings, localEnv}) => p.oneOf(
                    bodyForm => p.successResult({bindings, body: analyseValueExpr(localEnv, bodyForm)}).then(
                      ({bindings, body}) => p.parseEnd(new e.LetExpr({range, bindings, body}))))))
              .orThrow();

          case 'fn':
            throw 'NIY';
          }
        }

        throw 'NIY';
      }



    case 'symbol':
      throw 'NIY';

    default:
      throw 'unknown form?';
    }
  };

  if (form.formType == 'list') {
    const forms = form.forms;
    const firstForm = forms.first();
    if (firstForm.formType == 'symbol') {
      switch (firstForm.sym) {
      case 'def':
      case '::':
        throw 'NIY';

      default:
        return analyseValueExpr(im.Map(), form);
      }
    }

  } else {
    return analyseValueExpr(im.Map(), form);
  };
};

module.exports = {analyseNSForm, analyseForm};
