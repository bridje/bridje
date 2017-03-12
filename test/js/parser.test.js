var tok = require('../../src/js/tokeniser');
var p = require('../../src/js/parser');
var f = require('../../src/js/form');
var im = require('immutable');
var assert = require('assert');

describe('parser', () => {
  it('reads a string', () => {
    var res = p.parseForms(tok.tokenise('"Hello world!"')).first();
    assert(res.form instanceof f.StringForm);
    assert.equal(res.form.str, "Hello world!");
  });

  it('reads a list', () => {
    var res = p.parseForms(tok.tokenise('("Hello" "world!")')).first();
    assert(res.form instanceof f.ListForm);

    var forms = res.form.forms;
    assert.equal(forms.size, 2);

    assert(forms.get(0).form instanceof f.StringForm);
    assert.strictEqual(forms.get(0).form.str, "Hello");

    assert(forms.get(1).form instanceof f.StringForm);
    assert.strictEqual(forms.get(1).form.str, "world!");
  });

  it('reads a record', () => {
    var res = p.parseForms(tok.tokenise('{"a" "1", "b" "2"}')).first();

    assert(res.form instanceof f.RecordForm);
    var entries = res.form.entries;

    assert.equal(entries.size, 2);

    var entry0 = entries.get(0);
    var entry1 = entries.get(1);

    assert(entry0.key.form instanceof f.StringForm);
    assert.strictEqual(entry0.key.form.str, "a");

    assert(entry0.value.form instanceof f.StringForm);
    assert.strictEqual(entry0.value.form.str, "1");

    assert(entry1.key.form instanceof f.StringForm);
    assert.strictEqual(entry1.key.form.str, "b");

    assert(entry1.value.form instanceof f.StringForm);
    assert.strictEqual(entry1.value.form.str, "2");
  });

  it('reads an int', () => {
    var res = p.parseForms(tok.tokenise('-42')).first();
    assert(res.form instanceof f.IntForm);
    assert.strictEqual(res.form.int, -42);
  });

  it('reads a float', () => {
    var res = p.parseForms(tok.tokenise('42.54')).first();
    assert(res.form instanceof f.FloatForm);
    assert.strictEqual(res.form.float, 42.54);
  });

  it('reads a boolean', () => {
    var res = p.parseForms(tok.tokenise('false')).first();
    assert(res.form instanceof f.BoolForm);
    assert.strictEqual(res.form.bool, false);
  });

});
