var im = require('immutable');
var Record = im.Record;

var Form = Record({range: null, form: null});
var BoolForm = Record({bool: null});
var StringForm = Record({str: null});
var IntForm = Record({int: null});
var FloatForm = Record({float: null});
var VectorForm = Record({forms: null});
var SetForm = Record({forms: null});
var RecordEntry = Record({key: null, value: null});
var RecordForm = Record({entries: null});
var ListForm = Record({forms: null});
var SymbolForm = Record({sym: null});

Form.prototype.toString = function() {return this.form.toString();};

BoolForm.prototype.toString = function() {return `(BoolForm "${this.bool}")`;};
BoolForm.prototype.formType = 'bool';
StringForm.prototype.toString = function() {return `(StringForm "${this.str}")`;};
StringForm.prototype.formType = 'string';
IntForm.prototype.toString = function() {return `(IntForm ${this.int})`;};
IntForm.prototype.formType = 'int';
FloatForm.prototype.toString = function() {return `(FloatForm ${this.float})`;};
FloatForm.prototype.formType = 'float';

VectorForm.prototype.toString = function() {return `(VectorForm ${this.forms.join(' ')})`;};
VectorForm.prototype.formType = 'vector';
SetForm.prototype.toString = function() {return `(SetForm ${this.forms.join(' ')})`;};
SetForm.prototype.formType = 'set';

RecordEntry.prototype.toString = function() {return `${this.key} ${this.value}`;};
RecordForm.prototype.toString = function() {return `(RecordForm ${this.entries.join(', ')})`;};
RecordForm.prototype.formType = 'record';

ListForm.prototype.toString = function() {return `(ListForm ${this.forms.join(' ')})`;};
ListForm.prototype.formType = 'list';
SymbolForm.prototype.toString = function() {return `(SymbolForm ${this.sym})`;};
SymbolForm.prototype.formType = 'symbol';

module.exports = {
  Form,
  BoolForm, StringForm, IntForm, FloatForm,
  VectorForm, SetForm,
  RecordEntry, RecordForm,
  ListForm, SymbolForm
};
