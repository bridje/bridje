var tok = require('../lib/tokeniser');
var p = require('../lib/parser');
var f = require('../lib/form');
var im = require('immutable');
var assert = require('assert');

describe('parser', () => {
  it('reads a string', () => {
    assert(p.parseForms(tok.tokenise('"Hello world!"')).first().delete('range')
           .equals(new f.Form({form: new f.StringForm({str: "Hello world!"})})));
  });

  it('reads a list', () => {
    var res = p.parseForms(tok.tokenise('("Hello" "world!")')).first();
    assert(res.form instanceof f.ListForm);

    var forms = res.form.forms;
    assert.equal(forms.size, 2);

    assert(forms.get(0).form instanceof f.StringForm);
    assert.equal(forms.get(0).form.str, "Hello");

    assert(forms.get(1).form instanceof f.StringForm);
    assert.equal(forms.get(1).form.str, "world!");
  });

  it('reads a record', () => {
    var res = p.parseForms(tok.tokenise('{"a" "1", "b" "2"}')).first();

    assert(res.form instanceof f.RecordForm);
    var entries = res.form.entries;

    assert.equal(entries.size, 2);

    var entry0 = entries.get(0);
    var entry1 = entries.get(1);

    assert(entry0.key.form instanceof f.StringForm);
    assert.equal(entry0.key.form.str, "a");

    assert(entry0.value.form instanceof f.StringForm);
    assert.equal(entry0.value.form.str, "1");

    assert(entry1.key.form instanceof f.StringForm);
    assert.equal(entry1.key.form.str, "b");

    assert(entry1.value.form instanceof f.StringForm);
    assert.equal(entry1.value.form.str, "2");
  });


});
