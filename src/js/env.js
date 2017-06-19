const {List, Record, Map, Range} = require('immutable');
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

const NSHeader = Record({ns: null, brjFile: null, nsHash: null, forSyntax: null, refers: Map({}), aliases: Map({})});
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

  function makeVectorDataType(name, params) {
    const RecordConstructor = Record({_params: null});
    const value = ((...args) => new RecordConstructor({_params: List(args)}));
    RecordConstructor.prototype._brjType = value;
    return makeVar(name, value);
  };

  function makeRecordDataType(name, keys) {
    const RecordConstructor = Record(Map(List(keys).zip(Range(0, keys.size).map(_ => null))));
    const value = ((m) => new RecordConstructor(m));
    RecordConstructor.prototype._brjType = value;
    return makeVar(name, value);
  };

  function importRecordDataType(name, module) {
    const RecordConstructor = module[name];
    const value = (m) => new RecordConstructor(m);
    RecordConstructor.prototype._brjType = value;
    return makeVar(name, value);
  }

  const formExports = List.of('BoolForm', 'StringForm', 'IntForm', 'FloatForm',
                              'VectorForm', 'SetForm', 'RecordEntry', 'RecordForm',
                              'ListForm', 'SymbolForm', 'NamespacedSymbolForm')
        .map(name => [name, importRecordDataType(name, form)]);

  const exprExports = List.of('BoolExpr', 'StringExpr', 'IntExpr', 'FloatExpr',
                              'VectorExpr', 'SetExpr', 'RecordEntry', 'RecordExpr',
                              'IfExpr', 'LocalVarExpr', 'VarExpr', 'JSGlobalExpr',
                              'LetExpr', 'BindingPair',
                              'FnExpr', 'CallExpr',
                              'MatchClause', 'MatchExpr',
                              'DefExpr', 'DefDataExpr')
        .map(name => [name, importRecordDataType(name, expr)]);

  const formAccessors = List.of(['Form.range', form => form.range],
                                ['BoolForm.bool', form => form.bool],
                                ['StringForm.str', form => form.str],
                                ['IntForm.int', form => form.int],
                                ['FloatForm.float', form => form.float],
                                ['VectorForm.forms', form => form.forms],
                                ['SetForm.forms', form => form.forms]);

  const seqFns = List.of(['seq', coll => coll.toSeq()],
                         ['conj', (coll, el) => coll.push(el)],
                         ['head', coll => coll.first()],
                         ['tail', coll => coll.shift()],
                         ['empty?', coll => coll.isEmpty()]);

  const either = List.of(['Left', makeVectorDataType('Left', ['left'])],
                         ['Right', makeVectorDataType('Right', ['right'])],
                         ['Left.left', makeVar('Left.left', l => l.left)],
                         ['Right.right', makeVar('Right.right', r => r.right)]);

  return Map(
    formAccessors.concat(seqFns).map(([name, value]) => [name, makeVar(name, value)]))
    .merge(either)
    .merge(formExports, exprExports);

})();

module.exports = {Env, NSEnv, NSHeader, Var, Symbol, kernelExports, makeSafe, sym, nsSym};
