var tok = require('./tokeniser');
var parser = require('./parser');

module.exports = {
  readForms: function(str) {
    return parser.parseForms(tok.tokenise(str));
  }
};
