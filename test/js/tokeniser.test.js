var assert = require('assert');
var newLoc = require('../../src/js/location').newLoc;
var tok = require('../../src/js/tokeniser');
var priv = tok.private;
var Im = require('immutable');

describe("tokenising", () => {
  it('reads a char', () => {
    var res = priv.readChar('hello world', newLoc);
    res.loc = res.loc.toJS();
    assert.deepEqual(res,
                     {
                       ch: 'h',
                       s: 'ello world',
                       loc: {line: 1, col: 2}
                     });
  });

  it('slurps whitespace', () => {
    var res = priv.slurpWhitespace("   \n foo bar", newLoc);
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
    assert.deepEqual(Im.fromJS(tok.tokenise(`
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
