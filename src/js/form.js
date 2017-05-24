const im = require('immutable');
const Record = im.Record;

const BoolForm = Record({range: null, bool: null});
const StringForm = Record({range: null, str: null});
const IntForm = Record({range: null, int: null});
const FloatForm = Record({range: null, float: null});
const VectorForm = Record({range: null, forms: null});
const SetForm = Record({range: null, forms: null});
const RecordEntry = Record({key: null, value: null});
const RecordForm = Record({range: null, entries: null});
const ListForm = Record({range: null, forms: null});
const SymbolForm = Record({range: null, sym: null});
const NamespacedSymbolForm = Record({range: null, sym: null});
const QuotedForm = Record({range: null, form: null});

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

QuotedForm.prototype.toString = function() {return `(QuotedForm ${this.form})`;};
QuotedForm.prototype.formType = 'quoted';

module.exports = {
  BoolForm, StringForm, IntForm, FloatForm,
  VectorForm, SetForm,
  RecordEntry, RecordForm,
  ListForm, SymbolForm, NamespacedSymbolForm,
  QuotedForm
};
