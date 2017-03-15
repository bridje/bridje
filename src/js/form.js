var im = require('immutable');
var Record = im.Record;

var Form = Record({range: null, form: null});
var BoolForm = Record({bool: null});
var StringForm = Record({str: null});
var IntForm = Record({int: null});
var FloatForm = Record({float: null});
var ListForm = Record({forms: null});
var VectorForm = Record({forms: null});
var RecordEntry = Record({key: null, value: null});
var RecordForm = Record({entries: null});
var SetForm = Record({forms: null});
var SymbolForm = Record({sym: null});

Form.prototype.toString = function() {return this.form.toString();};

BoolForm.prototype.toString = function() {return `(BoolForm "${this.bool}")`;};
StringForm.prototype.toString = function() {return `(StringForm "${this.str}")`;};
IntForm.prototype.toString = function() {return `(IntForm ${this.int})`;};
FloatForm.prototype.toString = function() {return `(FloatForm ${this.float})`;};
SymbolForm.prototype.toString = function() {return `(SymbolForm ${this.sym})`;};
ListForm.prototype.toString = function() {return `(ListForm ${this.forms.join(' ')})`;};
VectorForm.prototype.toString = function() {return `(VectorForm ${this.forms.join(' ')})`;};
RecordEntry.prototype.toString = function() {return `${this.key} ${this.value}`;};
RecordForm.prototype.toString = function() {return `(RecordForm ${this.entries.join(', ')})`;};
SetForm.prototype.toString = function() {return `(SetForm ${this.forms.join(' ')})`;};

module.exports = {
  Form,
  BoolForm, StringForm, IntForm, FloatForm, SymbolForm,
  ListForm, RecordEntry, RecordForm, VectorForm, SetForm
};
