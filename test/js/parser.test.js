var p = require('../../src/js/parser');
var r = require('../../src/js/reader');
var Symbol = require('../../src/js/symbol');
var assert = require('assert');

describe('parsing', () => {
  it ('parses a symbol', () => {
    let res = p.parseForms(r.readForms('foo'), p.SymbolParser);
    assert(res.success);
    assert.equal(res.result.form.sym, Symbol.sym('foo'));
    console.log(res.result);
  });

  it ('fails to parse a symbol when you give it a list', () => {
    let res = p.parseForms(r.readForms('[]'), p.SymbolParser);
    assert(!res.success);
  });

});
