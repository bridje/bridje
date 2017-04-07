const {Record, Map} = require('immutable');

const Env = Record({nsEnvs: Map({})});
const NSEnv = Record({ns: null, exports: Map({}), refers: Map({}), aliases: Map({})});
const Var = Record({ns: null, name: null, value: undefined, safeName: undefined});

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

module.exports = {Env, NSEnv, Var, Symbol, sym, nsSym};
