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
