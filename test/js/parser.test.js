var im = require('immutable');
var p = require('../../src/js/parser');
var r = require('../../src/js/reader');
var {sym, Symbol} = require('../../src/js/env');
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
    let res = p.parseForms(im.List.of('foo', 'bar'), parser);

    assert(res.success);
    assert.equal(res.result, 'foo');
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

    let res0 = p.parseForms(im.List.of('invalid'), boundParser);
    assert(!res0.success);
    assert.equal(res0.error, 'boo');

    let res1 = p.parseForms(im.List.of('valid', 'invalid'), boundParser);
    assert(res1.success);
    assert.equal(res1.result, 'valid');

    let res2 = p.parseForms(im.List.of('valid', 'also-valid'), boundParser);
    assert(res2.success);
    assert.equal(res2.result, 'valid also-valid');
  });

  it ('parses a symbol', () => {
    let res = p.parseForms(r.readForms('foo'), p.SymbolParser);
    assert(res.success);

    let symbol = res.result.sym;
    assert(symbol instanceof Symbol);
    assert.equal(symbol.name, 'foo');
  });

  var nsSym = sym('ns');

  it ('parses an "ns" symbol', () => {
    let res0 = p.parseForms(r.readForms('ns'), p.isSymbol(nsSym));
    assert(res0.success);
    assert.equal(res0.result, nsSym);

    let res1 = p.parseForms(r.readForms('barf'), p.isSymbol(nsSym));
    assert(!res1.success);
  });

  it ('fails to parse a symbol when you give it a list', () => {
    let res = p.parseForms(r.readForms('[]'), p.SymbolParser);
    assert(!res.success);
  });

  it ('parses the end of the list', () => {
    let parser = p.isSymbol(nsSym).then(p.parseEnd);

    let res0 = p.parseForms(r.readForms('ns'), parser);
    assert.equal(res0.result, nsSym);

    let res1 = p.parseForms(r.readForms('ns and-more'), parser);
    assert(!res1.success);
  });

  it('parses innerForms', () => {
    let parser = p.ListParser.then(listForm => p.innerFormsParser(
      listForm.forms,
      p.isSymbol(nsSym).then(sym => p.parseEnd({isNS: true})),
      p.parseEnd));

    let res0 = p.parseForms(r.readForms('(ns)'), parser);
    assert(res0.success);
    assert.deepEqual(res0.result, {isNS: true});

    let res1 = p.parseForms(r.readForms('(boo)'), parser);
    assert(!res1.success);
  });
});
