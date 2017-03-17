var im = require('immutable');
var p = require('../../src/js/parser');
var r = require('../../src/js/reader');
var Symbol = require('../../src/js/symbol');
var assert = require('assert');

describe('parsing', () => {

  it ('fmaps a result', () => {
    assert.equal(p.successResult('foo').fmap(res => res + 'bar').result, 'foobar');
    assert.equal(p.failResult('foo').fmap(res => res + 'bar').error, 'foo');
  });

  it ('fmaps a parser', () => {
    let parser = new p.Parser(forms => ({
      result: p.successResult(forms.first()),
      forms: forms.shift()
    }));

    let res = parser.fmap(res => res + 'baz').parseForms(im.List.of('foo', 'bar'));

    assert(res.result.success);
    assert.equal(res.result.result, 'foobaz');
    assert.deepEqual(res.forms.toArray(), ['bar']);
  });

  it ('handles a oneOf parser', () => {
    let parser = p.oneOf(form => p.successResult(form));
    let res = parser.parseForms(im.List.of('foo', 'bar'));

    assert(res.result.success);
    assert.equal(res.result.result, 'foo');
    assert.deepEqual(res.forms.toArray(), ['bar']);
  });

  it ('binds a parser', () => {
    let parser = p.oneOf(form => {
      if (form == 'valid') {
        return p.successResult('valid');
      } else {
        return p.failResult('boo');
      }
    });

    let boundParser = parser.then(firstForm => p.oneOf(form => {
      if (form == 'also-valid') {
        return p.successResult(firstForm + ' ' + form);
      } else {
        return p.successResult(firstForm);
      }
    }));

    let res0 = boundParser.parseForms(im.List.of('invalid'));
    assert(!res0.result.success);
    assert.equal(res0.result.error, 'boo');

    let res1 = boundParser.parseForms(im.List.of('valid', 'invalid'));
    assert(res1.result.success);
    assert.equal(res1.result.result, 'valid');

    let res2 = boundParser.parseForms(im.List.of('valid', 'also-valid'));
    assert(res2.result.success);
    assert.equal(res2.result.result, 'valid also-valid');
  });

  it ('parses a symbol', () => {
    let res = p.parseForms(r.readForms('foo'), p.SymbolParser);
    assert(res.result.success);

    let symbol = res.result.result.form.sym;
    assert(symbol instanceof Symbol);
    assert.equal(symbol.name, 'foo');
  });

  it ('parses an "ns" symbol', () => {
    let nsSym = Symbol.sym('ns');

    let res0 = p.parseForms(r.readForms('ns'), p.isSymbol(nsSym));
    assert(res0.result.success);
    assert.equal(res0.result.result, nsSym);

    let res1 = p.parseForms(r.readForms('barf'), p.isSymbol(nsSym));
    assert(!res1.success);
  });

  it ('fails to parse a symbol when you give it a list', () => {
    let res = p.parseForms(r.readForms('[]'), p.SymbolParser);
    assert(!res.success);
  });

});
