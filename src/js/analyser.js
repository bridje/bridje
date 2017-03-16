var p = require('./parser');
var expr = require('./expr');
var Symbol = require('./symbol');

function analyseNSForm(env, ns, form) {
  return p.parseForm(form, p.ListParser.then(listForm => {
    console.log(p.isSymbol(Symbol.sym('ns')).parseForms(listForm.form.forms));
    return p.innerFormsParser(listForm.form.forms, p.isSymbol(Symbol.sym('ns')), p.pure);
  }));
}

module.exports = {analyseNSForm};
