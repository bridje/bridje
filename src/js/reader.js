const {fromJS, Record, Map, List} = require('immutable');
const {sym, nsSym} = require('./env');
const l = require('./location');
const f = require('./form');

const Token = Record({range: null, token: null, isString: false});

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

    if (/[`'()\[\]{}]/.test(nextCh.ch)) {
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
    } else if ('~' === nextCh.ch) {
      ({s, loc} = nextCh);
      nextCh = readChar(s, loc);

      if (nextCh === null) {
        throw new Error(`EOF after reading '~', at ${loc.toString()}`);
      } else if (nextCh.ch == '@') {
        return {
          s: nextCh.s,
          loc: nextCh.loc,
          token: new Token({
            range: l.range(startLoc, nextCh.loc),
            token: '~' + nextCh.ch
          })
        };
      } else {
        return {s, loc, token: new Token({token: '~', range: l.range(startLoc, startLoc)})};
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
  var tokens = new List();
  var token;
  while ((token = readToken(s, loc)) !== null) {
    ({s, loc} = token);
    tokens = tokens.push(token.token);
  }

  return tokens;
}


var eofBehaviour = {
  Throw: () => {
    throw new Error(`EOF while reading`);
  },
  Return: () => {
    return null;
  }
};

function nextToken(tokens) {
  return {
    token: tokens.first(),
    tokens: tokens.shift()
  };
}

function parseForm(tokens, closeParen, onEOF) {

}

function parseForm(tokens, closeParen, onEOF) {
  let token;
  ({token, tokens} = nextToken(tokens));

  if (token === undefined) {
    return onEOF();
  }

  if (token.isString) {
    return {tokens, form: new f.StringForm({range: token.range, str: token.token})};

  } else if (/^[-+]?\d+$/.test(token.token)) {
    return {tokens, form: new f.IntForm({range: token.range, int: parseInt(token.token)})};

  } else if (/^[-+]?\d+\.\d+$/.test(token.token)) {
    return {tokens, form: new f.FloatForm({range: token.range, float: parseFloat(token.token)})};

  } else {
    const parseSeq = (closeParen, makeForm) => {
      let innerForms = new List();

      while(true) {
        let form;
        ({form, tokens} = parseForm(tokens, closeParen, eofBehaviour.Throw));
        if (form) {
          innerForms = innerForms.push(form);
        } else {
          return {tokens, form: makeForm(token.range, innerForms)};
        }
      }
    };

    switch (token.token) {
    case 'true':
    case 'false':
      return {tokens, form: new f.BoolForm({range: token.range, bool: token.token === 'true'})};

    case '(':
      return parseSeq(')', (range, innerForms) => new f.ListForm({range, forms: innerForms}));

    case '[':
      return parseSeq(']', (range, innerForms) => new f.VectorForm({range, forms: innerForms}));

    case '{':
      return parseSeq('}', (range, innerForms) => {
        if (innerForms.size % 2 !== 0) {
          throw new Error(`Even number of forms expected in record at ${token.range}`);
        }

        let entries = [];
        innerForms = innerForms.toArray();
        for (let i = 0; i < innerForms.length; i += 2) {
          entries.push(new f.RecordEntry({key: innerForms[i], value: innerForms[i+1]}));
        }

        return new f.RecordForm({range, entries: List(entries)});
      });

    case '#{':
      return parseSeq('}', (range, innerForms) => new f.SetForm({range, forms: innerForms}));

    case ')':
    case '}':
    case ']':
      if (token.token === closeParen) {
        return {tokens};
      } else {
        const expecting = closeParen !== null ? `, expecting '${closeParen}'` : '';
        throw new Error(`Unexpected '${token.token}'${expecting}, at ${token.range.end}`);
      }

    case "'": {
      let form;
      ({form, tokens} = parseForm(tokens, null, eofBehaviour.Throw));
      return {tokens, form: new f.QuotedForm({range: token.range, form})};
    }

    case '`': {
      let form;
      ({form, tokens} = parseForm(tokens, null, eofBehaviour.Throw));
      return {tokens, form: new f.ListForm({
        range: l.Range({start: token.range.start, end: form.range.end}),
        forms: List.of(new f.NamespacedSymbolForm({range: token.range, sym: nsSym('bridje.kernel', 'syntax-quote')}), form)
      })};
    }

    case '~': {
      let form;
      ({form, tokens} = parseForm(tokens, null, eofBehaviour.Throw));
      return {tokens, form: new f.ListForm({
        range: l.Range({start: token.range.start, end: form.range.end}),
        forms: List.of(new f.NamespacedSymbolForm({range: token.range, sym: nsSym('bridje.kernel', 'syntax-unquote')}), form)
      })};
    }

    case '~@': {
      let form;
      ({form, tokens} = parseForm(tokens, null, eofBehaviour.Throw));
      return {tokens, form: new f.ListForm({
        range: l.Range({start: token.range.start, end: form.range.end}),
        forms: List.of(new f.NamespacedSymbolForm({range: token.range, sym: nsSym('bridje.kernel', 'syntax-unquote-splice')}), form)
      })};
    }

    default:
      let symMatch = token.token.match(/^([\w\-<>\.?!]+)$/u);
      if (symMatch) {
        return {tokens, form: new f.SymbolForm({range: token.range, sym: sym(token.token)})};
      }

      let nsSymMatch = token.token.match(/^([\w\-<>\.?!]+)\/([\w\-<>\.?!]+)$/u);
      if (nsSymMatch) {
        let [_, ns, name] = nsSymMatch;
        return {tokens, form: new f.NamespacedSymbolForm({range: token.range, sym: nsSym(ns, name)})};
      }

      throw new Error(`Invalid symbol '${token.token}', at ${token.range.start}`);
    }
  }
}

function parseForms(tokens) {
  let forms = List();
  while(true) {
    let form;
    ({form, tokens} = parseForm(tokens, null, eofBehaviour.Return));
    if (form) {
      forms = forms.push(form);
    }

    if (tokens.isEmpty()){
      return forms;
    }
  }
}

module.exports = {
  readForms: function(str) {
    return parseForms(tokenise(str));
  },

  _tokeniser: {readChar, slurpWhitespace, readToken, tokenise},
  _parser: {parseForms}
};
