var im = require('immutable');

var Symbol = im.Record({ns: null, name: null});

Symbol.prototype.toString = function() {
  if(this.ns !== null) {
    return `${this.ns}/${this.name}`;
  } else {
    return this.name;
  }
};

module.exports = Symbol;

module.exports.sym = function(name) {
  return new Symbol({name});
};

module.exports.nsSym = function(ns, name) {
  return new Symbol({ns, name});
};
