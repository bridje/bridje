var im = require('immutable');
var Record = im.Record;

var Expr = Record({range: null, expr: null});
var BoolExpr = Record({bool: null});
var StringExpr = Record({str: null});
var IntExpr = Record({int: null});
var FloatExpr = Record({float: null});
var VectorExpr = Record({exprs: null});
var SetExpr = Record({exprs: null});
var RecordEntry = Record({key: null, value: null});
var RecordExpr = Record({entries: null});

Expr.prototype.toString = function() {return this.expr.toString();};

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


module.exports = {
  Expr,
  BoolExpr, StringExpr, IntExpr, FloatExpr,
  VectorExpr, SetExpr,
  RecordEntry, RecordExpr
};
