const {Record, Map} = require('immutable');

const Env = Record({nsEnvs: Map({})});
const NSEnv = Record({ns: null, exports: Map({})});
const Var = Record({ns: null, name: null, value: undefined, safeName: undefined});

var Symbol = Record({ns: null, name: null});

Symbol.prototype.toString = function() {
  if(this.ns !== null) {
    return `${this.ns}/${this.name}`;
  } else {
    return this.name;
  }
};

function sym(name) {
  return new Symbol({name});
};

function nsSym(ns, name) {
  return new Symbol({ns, name});
};

module.exports = {Env, NSEnv, Var, Symbol, sym, nsSym};
