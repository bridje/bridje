const {Record} = require('immutable');

const BoolExpr = Record({range: null, bool: null});
const StringExpr = Record({range: null, str: null});
const IntExpr = Record({range: null, int: null});
const FloatExpr = Record({range: null, float: null});
const VectorExpr = Record({range: null, exprs: null});
const SetExpr = Record({range: null, exprs: null});
const RecordEntry = Record({key: null, value: null});
const RecordExpr = Record({range: null, entries: null});

const IfExpr = Record({range: null, testExpr: null, thenExpr: null, elseExpr: null});

const LocalVarExpr = Record({range: null, name: null, localVar: null});
const VarExpr = Record({range: null, var: null});

const LetBinding = Record({localVar: null, expr: null});
const LetExpr = Record({range: null, bindings: null, body: null});

const FnExpr = Record({range: null, params: null, body: null});
const CallExpr = Record({range: null, exprs: null});

const DefExpr = Record({range: null, sym: null, params: null, body: null});

BoolExpr.prototype.toString = function() {return `(BoolExpr ${this.bool})`;};
BoolExpr.prototype.exprType = 'bool';
StringExpr.prototype.toString = function() {return `(StringExpr "${this.str}")`;};
StringExpr.prototype.exprType = 'string';
IntExpr.prototype.toString = function() {return `(IntExpr ${this.int})`;};
IntExpr.prototype.exprType = 'int';
FloatExpr.prototype.toString = function() {return `(FloatExpr ${this.float})`;};
FloatExpr.prototype.exprType = 'float';
VectorExpr.prototype.toString = function() {return `(VectorExpr ${this.exprs.join(' ')})`;};
VectorExpr.prototype.exprType = 'vector';
SetExpr.prototype.toString = function() {return `(SetExpr ${this.exprs.join(' ')})`;};
SetExpr.prototype.exprType = 'set';
RecordEntry.prototype.toString = function() {return `${this.key} ${this.value}`;};
RecordExpr.prototype.toString = function() {return `(RecordExpr {${this.entries.join(', ')})}`;};
RecordExpr.prototype.exprType = 'record';

IfExpr.prototype.toString = function() {return `(IfExpr ${this.testExpr} ${this.thenExpr} ${this.elseExpr})`;};
IfExpr.prototype.exprType = 'if';

LocalVarExpr.prototype.toString = function() {return `(LocalVarExpr ${this.name})`;};
LocalVarExpr.prototype.exprType = 'localVar';

VarExpr.prototype.toString = function() {return `(VarExpr ${this.var.ns}/${this.var.name})`;};
VarExpr.prototype.exprType = 'var';

LetBinding.prototype.toString = function() {return `${this.name} ${this.expr}`;};
LetExpr.prototype.toString = function() {return `(LetExpr [${this.bindings.join(', ')}] ${this.body})`;};
LetExpr.prototype.exprType = 'let';

FnExpr.prototype.toString = function() {return `(FnExpr (${this.params.map(p => p.name).join(' ')}) ${this.body})`;};
FnExpr.prototype.exprType = 'fn';
CallExpr.prototype.toString = function() {return `(CallExpr ${this.exprs.join(' ')})`;};
CallExpr.prototype.exprType = 'call';

DefExpr.prototype.toString = function() {return `(DefExpr ${this.sym} ${this.body})`;};
DefExpr.prototype.exprType = 'def';

module.exports = {
  BoolExpr, StringExpr, IntExpr, FloatExpr,
  VectorExpr, SetExpr,
  RecordEntry, RecordExpr,
  IfExpr, LocalVarExpr, VarExpr, LetExpr, LetBinding,
  FnExpr, CallExpr, DefExpr
};
