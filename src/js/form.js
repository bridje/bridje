var im = require('immutable');
var Record = im.Record;

var BoolForm = Record({range: null, bool: null});
var StringForm = Record({range: null, str: null});
var IntForm = Record({range: null, int: null});
var FloatForm = Record({range: null, float: null});
var VectorForm = Record({range: null, forms: null});
var SetForm = Record({range: null, forms: null});
var RecordEntry = Record({key: null, value: null});
var RecordForm = Record({range: null, entries: null});
var ListForm = Record({range: null, forms: null});
var SymbolForm = Record({range: null, sym: null});
var NamespacedSymbolForm = Record({range: null, sym: null});

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
RecordForm.prototype.toString = function() {return `(RecordForm {${this.entries.join(', ')})}`;};
RecordForm.prototype.formType = 'record';

ListForm.prototype.toString = function() {return `(ListForm ${this.forms.join(' ')})`;};
ListForm.prototype.formType = 'list';

SymbolForm.prototype.toString = function() {return `(SymbolForm ${this.sym})`;};
SymbolForm.prototype.formType = 'symbol';
NamespacedSymbolForm.prototype.toString = function() {return `(NamespacedSymbolForm ${this.sym})`;};
NamespacedSymbolForm.prototype.formType = 'namespacedSymbol';

module.exports = {
  BoolForm, StringForm, IntForm, FloatForm,
  VectorForm, SetForm,
  RecordEntry, RecordForm,
  ListForm, SymbolForm, NamespacedSymbolForm
};
