var im = require('immutable');
var Record = im.Record;

var Form = Record({range: null, form: null});
var StringForm = Record({str: null});
var ListForm = Record({forms: null});

Form.prototype.toString = function() {return this.form.toString();};

StringForm.prototype.toString = function() {return `(StringForm "${this.str}")`;};
ListForm.prototype.toString = function() {return `(ListForm ${this.forms.join(' ')})`;};

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
      forms = forms.push(new Form({
        range: token.range,
        form: new StringForm({str: token.token})
      }));
    } else {
      switch (token.token) {
      case '(':
        var innerForms;
        ({forms: innerForms, tokens} = parseForms0(tokens, ')', eofBehaviour.Throw));

        forms = forms.push(new Form({
          range: token.range,
          form: new ListForm({forms: innerForms})
        }));
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
  parseForms: parseForms,
  Form: Form,
  StringForm: StringForm,
  ListForm: ListForm
};
