var p = require('./parser');
var expr = require('./expr');

function analyseNSForm(env, ns, form) {
  return p.parseForm(form, p.ListParser.bind(list => {
    return p.pure(list);
  }));
}

module.exports = {analyseNSForm};
