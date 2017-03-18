var im = require('immutable');
var p = require('./parser');
var e = require('./expr');
var f = require('./form');
var Symbol = require('./symbol');

function analyseNSForm(env, ns, form) {
  return p.parseForm(form, p.ListParser.then(listForm => {
    console.log(p.isSymbol(Symbol.sym('ns')).parseForms(listForm.form.forms));
    return p.innerFormsParser(listForm.form.forms, p.isSymbol(Symbol.sym('ns')), p.pure);
  }));
}

function analyseForm(env, nsEnv, form) {
  function analyseValueExpr(localEnv, form) {
    const range = form.range;
    form = form.form;

    switch(form.formType) {
    case 'bool':
      return new e.Expr({range, expr: new e.BoolExpr({bool: form.bool})});
    case 'string':
      return new e.Expr({range, expr: new e.StringExpr({str: form.str})});
    case 'int':
      return new e.Expr({range, expr: new e.IntExpr({int: form.int})});
    case 'float':
      return new e.Expr({range, expr: new e.FloatExpr({float: form.float})});

    case 'vector':
      return new e.Expr({range, expr: new e.VectorExpr({exprs: form.forms.map(f => analyseValueExpr(localEnv, f))})});
    case 'set':
      return new e.Expr({range, expr: new e.SetExpr({exprs: form.forms.map(f => analyseValueExpr(localEnv, f))})});

    case 'record':
    case 'list':
    case 'symbol':
      throw 'NIY';

    default:
      throw 'unknown form?';
    }
  };

  if (form.form.formType == 'list') {
    throw 'NIY';
  } else {
    return analyseValueExpr(im.Map(), form);
  };
};

module.exports = {analyseNSForm, analyseForm};
