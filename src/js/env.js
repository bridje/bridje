const {Record, Map} = require('immutable');

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

module.exports = {Env, NSEnv, NSHeader, Var, Symbol, sym, nsSym};
