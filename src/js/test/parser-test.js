var tok = require('../lib/tokeniser');
var p = require('../lib/parser');
var im = require('immutable');
var assert = require('assert');

describe('parser', () => {
  it('reads a string', () => {
    assert(p.parseForms(tok.tokenise('"Hello world!"')).first().delete('range')
           .equals(new p.Form({form: new p.StringForm({str: "Hello world!"})})));
  });

  it('reads a list', () => {
    var res = p.parseForms(tok.tokenise('("Hello" "world!")')).first();
    // TODO how to test this?
  });

});
