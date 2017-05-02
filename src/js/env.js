const {List, Record, Map} = require('immutable');
const expr = require('./expr');

const Env = Record({nsEnvs: Map({})});
const NSEnv = Record({
  ns: null, brjFile: null, nsHash: null,
  exports: Map({}),
  refers: Map({}), aliases: Map({})
});

const NSHeader = Record({ns: null, brjFile: null, nsHash: null, refers: Map({}), aliases: Map({})});
const Var = Record({ns: null, name: null, expr: undefined, value: undefined, safeName: undefined});

var Symbol = Record({name: null});
var NamespacedSymbol = Record({ns: null, name: null});

Symbol.prototype.toString = function() {
  return this.name;
};

function sym(name) {
  return new Symbol({name});
};

function nsSym(ns, name) {
  return new NamespacedSymbol({ns, name});
};

const kernelExports = (function() {
  function makeDataType(name) {
    const RecordConstructor = expr[name];
    const value = (m) => new RecordConstructor(m);
    RecordConstructor.prototype._brjType = value;
    return [name, new Var({ns: 'bridje.kernel', name, safeName: name, value})];
  }

  return Map(List.of('BoolExpr', 'StringExpr', 'IntExpr', 'FloatExpr',
                     'VectorExpr', 'SetExpr',
                     'RecordEntry', 'RecordExpr',
                     'IfExpr', 'LocalVarExpr', 'VarExpr', 'JSGlobalExpr',
                     'LetExpr', 'LetBinding',
                     'FnExpr', 'CallExpr',
                     'MatchClause', 'MatchExpr',
                     'DefExpr', 'DefDataExpr')
             .map(makeDataType));
})();

module.exports = {Env, NSEnv, NSHeader, Var, Symbol, kernelExports, sym, nsSym};
