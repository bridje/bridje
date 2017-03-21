var im = require('immutable');
var Record = im.Record;

var BoolExpr = Record({range: null, bool: null});
var StringExpr = Record({range: null, str: null});
var IntExpr = Record({range: null, int: null});
var FloatExpr = Record({range: null, float: null});
var VectorExpr = Record({range: null, exprs: null});
var SetExpr = Record({range: null, exprs: null});
var RecordEntry = Record({key: null, value: null});
var RecordExpr = Record({range: null, entries: null});

var IfExpr = Record({range: null, testExpr: null, thenExpr: null, elseExpr: null});

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



module.exports = {
  BoolExpr, StringExpr, IntExpr, FloatExpr,
  VectorExpr, SetExpr,
  RecordEntry, RecordExpr,
  IfExpr
};
