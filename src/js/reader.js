var im = require('immutable');
var Record = im.Record;
var Map = im.Map;
var {sym, nsSym} = require('./env');
var l = require('./location');
var f = require('./form');

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
    } else if (';' === next.ch) {
      ({s, loc} = next);

      while (s !== "") {
        next = readChar(s, loc);
        ({s, loc} = next);

        if (/[\r\n]/.test(next.ch)) {
          break;
        }
      }
    } else {
      break;
    }
  };

  return {
    s: s,
    loc: loc
  };
}

var delimiterRegex = /[,\s\(\)\[\]\{\}#;]/;

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

    if (/[()\[\]{}]/.test(nextCh.ch)) {
      return {
        s: nextCh.s,
        loc: nextCh.loc,
        token: new Token({
          range: l.range(startLoc, nextCh.loc),
          token: nextCh.ch
        })};
    } else if ('#' === nextCh.ch) {
      ({s, loc} = nextCh);
      nextCh = readChar(s, loc);

      if (nextCh === null) {
        throw new Error(`EOF after reading '#', at ${loc.toString()}`);
      } else if (/['{]/.test(nextCh.ch)) {
        return {
          s: nextCh.s,
          loc: nextCh.loc,
          token: new Token({
            range: l.range(startLoc, nextCh.loc),
            token: '#' + nextCh.ch
          })
        };
      } else {
        throw new Error(`Unexpected character '${nextCh.ch}' '#', at ${loc.toString()}`);
      }
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
            throw new Error(`EOF after reading '\', at ${loc.toString()}`);
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
  var loc = l.newLoc;
  var tokens = new im.List();
  var token;
  while ((token = readToken(s, loc)) !== null) {
    ({s, loc} = token);
    tokens = tokens.push(token.token);
  }

  return tokens;
}


var eofBehaviour = {
  Throw: (forms) => {
    throw new Error(`EOF while reading`);
  },
  Return: (forms) => {
    return forms;
  }
};

function nextToken(tokens) {
  return {
    token: tokens.first(),
    tokens: tokens.shift()
  };
}

function parseForms0(tokens, closeParen, onEOF) {
  var forms = im.List();

  var token;

  while (true) {
    ({token, tokens} = nextToken(tokens));

    if (token === undefined) {
      return {forms: onEOF(forms)};
    }

    if (token.isString) {
      forms = forms.push(new f.StringForm({range: token.range, str: token.token}));

    } else if (/^[-+]?\d+$/.test(token.token)) {
      forms = forms.push(new f.IntForm({range: token.range, int: parseInt(token.token)}));

    } else if (/^[-+]?\d+\.\d+$/.test(token.token)) {
      forms = forms.push(new f.FloatForm({range: token.range, float: parseFloat(token.token)}));

    } else {
      var parseSeq = (closeParen, makeForm) => {
        var innerForms;

        ({forms: innerForms, tokens} = parseForms0(tokens, closeParen, eofBehaviour.Throw));

        return {
          forms: forms.push(makeForm(token.range, innerForms)),
          tokens
        };
      };

      switch (token.token) {
      case 'true':
      case 'false':
        forms = forms.push(new f.BoolForm({range: token.range, bool: token.token === 'true'}));
        break;

      case '(':
        ({forms, tokens} = parseSeq(')', (range, innerForms) => new f.ListForm({range, forms: innerForms})));
        break;

      case '[':
        ({forms, tokens} = parseSeq(']', (range, innerForms) => new f.VectorForm({range, forms: innerForms})));
        break;

      case '{':
        ({forms, tokens} = parseSeq('}', (range, innerForms) => {
          if (innerForms.size % 2 !== 0) {
            throw new Error(`Even number of forms expected in record at ${token.range}`);
          }

          var entries = [];
          innerForms = innerForms.toArray();
          for (let i = 0; i < innerForms.length; i += 2) {
            entries.push(new f.RecordEntry({key: innerForms[i], value: innerForms[i+1]}));
          }


          return new f.RecordForm({range, entries: im.fromJS(entries)});
        }));

        break;

      case '#{':
        ({forms, tokens} = parseSeq('}', (range, innerForms) => new f.SetForm({range, forms: innerForms})));
        break;

      case ')':
      case '}':
      case ']':
        if (token.token === closeParen) {
          return {
            forms: forms,
            tokens: tokens
          };
} else {
          var expecting = closeParen !== null ? `, expecting '${closeParen}'` : '';
          throw new Error(`Unexpected '${token.token}'${expecting}, at ${token.range.end}`);
        }

        break;

      default:
        let symMatch = token.token.match(/^[\w\-<>\.]+$/u);
        if (symMatch) {
          forms = forms.push(new f.SymbolForm({range: token.range, sym: sym(token.token)}));
          break;
        }

        let nsSymMatch = token.token.match(/^[\w\-<>\.]+\/[\w\-<>\.]+$/u);
        if (nsSymMatch) {
          let [_, ns, name] = nsSymMatch;
          forms = forms.push(new f.NamespacedSymbolForm({range: token.range, sym: nsSym(ns, name)}));
          break;
        }

        throw new Error(`Invalid symbol '${token.token}', at ${token.range.start}`);
      }
    }
  }
}

function parseForms(tokens) {
  return parseForms0(tokens, null, eofBehaviour.Return).forms;
}

module.exports = {
  readForms: function(str) {
    return parseForms(tokenise(str));
  },

  _tokeniser: {readChar, slurpWhitespace, readToken, tokenise},
  _parser: {parseForms}
};
