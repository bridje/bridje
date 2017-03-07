var f = require('./form');
var im = require('immutable');
var Record = im.Record;

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
      forms = forms.push(new f.Form({
        range: token.range,
        form: new f.StringForm({str: token.token})
      }));

    } else if (/^[-+]?\d+$/.test(token.token)) {
      forms = forms.push(new f.Form({
        range: token.range,
        form: new f.IntForm({int: parseInt(token.token)})
      }));

    } else if (/^[-+]?\d+\.\d+$/.test(token.token)) {
      forms = forms.push(new f.Form({
        range: token.range,
        form: new f.FloatForm({float: parseFloat(token.token)})
      }));

    } else {
      var parseSeq = (closeParen, makeForm) => {
        var innerForms;

        ({forms: innerForms, tokens} = parseForms0(tokens, closeParen, eofBehaviour.Throw));

        return {
          forms: forms.push(new f.Form({
            range: token.range,
            form: makeForm(innerForms)
          })),

          tokens: tokens
        };
      };

      switch (token.token) {
      case '(':
        ({forms, tokens} = parseSeq(')', innerForms => new f.ListForm({forms: innerForms})));
        break;

      case '[':
        ({forms, tokens} = parseSeq(']', innerForms => new f.VectorForm({forms: innerForms})));
        break;

      case '{':
        ({forms, tokens} = parseSeq('}', innerForms => {
          if (innerForms.size % 2 !== 0) {
            throw new Error(`Even number of forms expected in record at ${token.range}`);
          }

          var entries = [];
          innerForms = innerForms.toArray();
          for (var i = 0; i < innerForms.length; i += 2) {
            entries.push(new f.RecordEntry({key: innerForms[i], value: innerForms[i+1]}));
          }


          return new f.RecordForm({entries: im.fromJS(entries)});
        }));

        break;

      case '#{':
        ({forms, tokens} = parseSeq('}', innerForms => new f.SetForm({forms: innerForms})));
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
      }
    }
  }
}

function parseForms(tokens) {
  return parseForms0(tokens, null, eofBehaviour.Return).forms;
}

module.exports = {
  parseForms: parseForms
};
