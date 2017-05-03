const {List, Record, Map} = require('immutable');
const expr = require('./expr');
const form = require('./form');

function makeSafe(s) {
  return s.replace(/[\-.<>?!]/g, m => ({'-': '__', '_': '_us', '.': '_dot',
                                      '?': '_qm', '!': '_ex',
                                      '<': '_lt', '>': '_gt', '=': '_eq'}[m]));
};

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
  function makeVar(name, value) {
    return new Var({ns: 'bridje.kernel', name, safeName: makeSafe(name), value});
  }

  function makeDataType(name, module) {
    const RecordConstructor = module[name];
    const value = (m) => new RecordConstructor(m);
    RecordConstructor.prototype._brjType = value;
    return makeVar(name, value);
  }


  const formExports = List.of('BoolForm', 'StringForm', 'IntForm', 'FloatForm',
                              'VectorForm', 'SetForm', 'RecordEntry', 'RecordForm',
                              'ListForm', 'SymbolForm', 'NamespacedSymbolForm')
        .map(name => [name, makeDataType(name, form)]);

  const exprExports = List.of('BoolExpr', 'StringExpr', 'IntExpr', 'FloatExpr',
                              'VectorExpr', 'SetExpr', 'RecordEntry', 'RecordExpr',
                              'IfExpr', 'LocalVarExpr', 'VarExpr', 'JSGlobalExpr',
                              'LetExpr', 'BindingPair',
                              'FnExpr', 'CallExpr',
                              'MatchClause', 'MatchExpr',
                              'DefExpr', 'DefDataExpr')
        .map(name => [name, makeDataType(name, expr)]);

  return Map(
    List.of(['Form.range', form => form.range],
            ['BoolForm.bool', form => form.bool],
            ['StringForm.str', form => form.str],
            ['IntForm.int', form => form.int],
            ['FloatForm.float', form => form.float])
      .map(([name, value]) => [name, makeVar(name, value)]))
    .merge(formExports, exprExports);

})();

module.exports = {Env, NSEnv, NSHeader, Var, Symbol, kernelExports, makeSafe, sym, nsSym};
