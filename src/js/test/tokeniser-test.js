var assert = require('assert');
var newLoc = require('../lib/location').newLoc;
var tok = require('../lib/tokeniser');
var priv = tok.private;

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

});
