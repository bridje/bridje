var im = require('immutable');
var Record = im.Record;

var Form = Record({range: null, form: null});
var StringForm = Record({str: null});
var ListForm = Record({forms: null});
var VectorForm = Record({forms: null});
var RecordEntry = Record({key: null, value: null});
var RecordForm = Record({entries: null});
var SetForm = Record({forms: null});

Form.prototype.toString = function() {return this.form.toString();};

StringForm.prototype.toString = function() {return `(StringForm "${this.str}")`;};
ListForm.prototype.toString = function() {return `(ListForm ${this.forms.join(' ')})`;};
VectorForm.prototype.toString = function() {return `(VectorForm ${this.forms.join(' ')})`;};
RecordEntry.prototype.toString = function() {return `${this.key} ${this.value}`;};
RecordForm.prototype.toString = function() {return `(RecordForm ${this.entries.join(', ')})`;};
SetForm.prototype.toString = function() {return `(SetForm ${this.forms.join(' ')})`;};

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
      var parseSeq = (closeParen, makeForm) => {
        var innerForms;

        ({forms: innerForms, tokens} = parseForms0(tokens, closeParen, eofBehaviour.Throw));

        return {
          forms: forms.push(new Form({
            range: token.range,
            form: makeForm(innerForms)
          })),

          tokens: tokens
        };
      };

      switch (token.token) {
      case '(':
        ({forms, tokens} = parseSeq(')', innerForms => new ListForm({forms: innerForms})));
        break;

      case '[':
        ({forms, tokens} = parseSeq(']', innerForms => new VectorForm({forms: innerForms})));
        break;

      case '{':
        ({forms, tokens} = parseSeq('}', innerForms => {
          if (innerForms.size % 2 !== 0) {
            throw new Error(`Even number of forms expected in record at ${token.range}`);
          }

          var entries = [];
          innerForms = innerForms.toArray();
          for (var i = 0; i < innerForms.length; i += 2) {
            entries.push(new RecordEntry({key: innerForms[i], value: innerForms[i+1]}));
          }


          return new RecordForm({entries: im.fromJS(entries)});
        }));

        break;

      case '#{':
        ({forms, tokens} = parseSeq('}', innerForms => new SetForm({forms: innerForms})));
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
