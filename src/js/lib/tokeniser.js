var Record = require('immutable').Record;
var Map = require('immutable').Map;
var l = require('./location');

var Token = Record({range: null, token: null, isString: false});

function readChar(s, loc) {
  if (s !== "") {
    var ch = s.charAt(0);
    return {
      ch: ch,
      s: s.substr(1),
      loc: l.moveLoc(ch, loc)
    };
  } else {
    return null;
  }
}

function slurpWhitespace(s, loc) {
  while (s !== "") {
    var next = readChar(s, loc);

    if (/[\s,]/.test(next.ch)) {
      ({s, loc} = next);
    } else {
      break;
    }
  };

  return {
    s: s,
    loc: loc
  };
}

var delimiterRegex = /[,\s\(\)\[\]\{\}#]/;

var escapes = {
  'n': "\n",
  'r': "\r",
  't': "\t",
  '"': '"',
  "\\": "\\"
};

function readToken (s, loc) {
  ({s, loc} = slurpWhitespace(s, loc));

  var startLoc = loc;

  if (s === "") {
    return null;
  } else {

    var nextCh = readChar(s, loc);

    if (/\(\)\[\]\{\}/.test(nextCh.ch)) {
      return new Token({
        range: l.range(startLoc, nextCh.loc),
        token: nextCh.ch
      });
    } else if ('"' === nextCh.ch) {
      var str = "";

      while (s !== "") {
        ({s, loc} = nextCh);
        nextCh = readChar(s, loc);

        if ('"' === nextCh.ch) {
          return {
            s: nextCh.s,
            loc: nextCh.loc,
            token: new Token({
              range: l.range(startLoc, nextCh.loc),
              isString: true,
              token: str
            })
          };
        } else if ('\\' === nextCh.ch) {
          ({s, loc} = nextCh);
          nextCh = readChar(s, loc);

          if (nextCh === null) {
            break; // eof
          }

          ({s, loc} = nextCh);
          str = str + escapes[nextCh.ch];
        } else {
          str = str + nextCh.ch;
        }
      }

      throw new Error(`EOF reading string, at ${loc.toString()}`);
    } else {
      var token = "";

      while (s !== "") {
        nextCh = readChar(s, loc);
        if (delimiterRegex.test(nextCh.ch)) {
          break;
        } else {
          token = token + nextCh.ch;
          ({s, loc} = nextCh);
        }
      }

      return {
        s: s,
        loc: loc,
        token: new Token({
          range: l.range(startLoc, loc),
          token: token
        })
      };
    }
  }
}

function tokenise(s) {
  var loc = loc.newLoc;
  var tokens = [];
  var token;
  while ((token = readToken(s, loc)) !== null) {
    s = token.s;
    loc = token.loc;
    tokens.push(token.token);
  }

  return tokens;
}

module.exports = {
  tokenise: tokenise,
  Token: Token,

  readToken: readToken,

  private: {
    readChar: readChar,
    slurpWhitespace: slurpWhitespace
  }
};
