const {Record} = require('immutable');

let nextIdx = 0;

const LocalVar = Record({name: null, idx: null});

module.exports = name => new LocalVar({name, idx: nextIdx++});
