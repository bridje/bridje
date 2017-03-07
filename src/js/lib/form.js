var im = require('immutable');
var Record = im.Record;

var Form = Record({range: null, form: null});
var StringForm = Record({str: null});
var IntForm = Record({int: null});
var FloatForm = Record({float: null});
var ListForm = Record({forms: null});
var VectorForm = Record({forms: null});
var RecordEntry = Record({key: null, value: null});
var RecordForm = Record({entries: null});
var SetForm = Record({forms: null});

Form.prototype.toString = function() {return this.form.toString();};

StringForm.prototype.toString = function() {return `(StringForm "${this.str}")`;};
IntForm.prototype.toString = function() {return `(IntForm ${this.int})`;};
FloatForm.prototype.toString = function() {return `(FloatForm ${this.float})`;};
ListForm.prototype.toString = function() {return `(ListForm ${this.forms.join(' ')})`;};
VectorForm.prototype.toString = function() {return `(VectorForm ${this.forms.join(' ')})`;};
RecordEntry.prototype.toString = function() {return `${this.key} ${this.value}`;};
RecordForm.prototype.toString = function() {return `(RecordForm ${this.entries.join(', ')})`;};
SetForm.prototype.toString = function() {return `(SetForm ${this.forms.join(' ')})`;};

module.exports = {
  Form: Form,
  StringForm: StringForm,
  IntForm: IntForm,
  FloatForm: FloatForm,
  ListForm: ListForm,
  RecordEntry: RecordEntry,
  RecordForm: RecordForm,
  VectorForm: VectorForm,
  SetForm: SetForm
};
