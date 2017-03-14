var assert = require('assert');
var newLoc = require('../../src/js/location').newLoc;
var reader = require('../../src/js/reader');
var tok = reader._tokeniser;
var p = reader._parser;
var im = require('immutable');

var f = require('../../src/js/form');

describe('reading', () => {
  describe("tokenising", () => {
    it('reads a char', () => {
      var res = tok.readChar('hello world', newLoc);
      res.loc = res.loc.toJS();
      assert.deepEqual(res,
                       {
                         ch: 'h',
                         s: 'ello world',
                         loc: {line: 1, col: 2}
                       });
    });

    it('slurps whitespace', () => {
      var res = tok.slurpWhitespace("   \n foo bar", newLoc);
      res.loc = res.loc.toJS();

      assert.deepEqual(res,
                       {
                         s: 'foo bar',
                         loc: {line: 2, col: 2}
                       });
    });


    it('reads a paren', () => {
      assert.equal(tok.readToken(' (-42 bell)', newLoc).token.token,
                   "(");
    });

    it('reads an int', () => {
      assert.equal(tok.readToken(' -42 bell', newLoc).token.token,
                   "-42");
    });

    it('reads a string', () => {
      var tokResult = tok.readToken('  "Hello world"', newLoc);
      assert.equal(tokResult.token.token, 'Hello world');
      assert(tokResult.token.isString === true);
    });

    it('reads a string with escapes', () => {
      var tokResult = tok.readToken('  "Hello\\n world \\\\ "', newLoc);
      assert.equal(tokResult.token.token, "Hello\n world \\ ");
      assert(tokResult.token.isString === true);
    });

    it('reads a #{} open paren', () => {
      assert.equal(tok.readToken('  #{"Hello world!"}', newLoc).token.token, "#{");
    });

    it("ignores to EoL after a ';'", () => {
      assert.equal(tok.readToken("  ;; here's a comment \n 42", newLoc).token.token, "42");
    });

    it("tokenises a form", () => {
      assert.deepEqual(im.fromJS(tok.tokenise(`
(def hello
  ;; A simple hello world!
  "Hello world!")
`)).toJS(),
                       [{
                         "isString": false,
                         "range": {"start": {"line": 2, "col": 1}, "end": {"line": 2, "col": 1}},
                         "token": "("
                       },
                        {
                          "isString": false,
                          "range": {"start": {"line": 2, "col": 2}, "end": {"line": 2, "col": 4}},
                          "token": "def"
                        },
                        {
                          "isString": false,
                          "range": {"start": {"line": 2, "col": 6}, "end": {"line": 2, "col": 10}},
                          "token": "hello"
                        },
                        {
                          "isString": true,
                          "range": {"start": {"line": 4, "col": 3}, "end": {"line": 4, "col": 16}},
                          "token": "Hello world!"
                        },
                        {
                          "isString": false,
                          "range": {"start": {"line": 4, "col": 17}, "end": {"line": 4, "col": 17}},
                          "token": ")"
                        }]);
    });

  });



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
});
