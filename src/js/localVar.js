var Record = require('immutable').Record;

const LocalVar = Record({name: null});

module.exports = name => new LocalVar({name});
