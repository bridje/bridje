const {Record} = require('immutable');

const LocalVar = Record({name: null});

module.exports = name => new LocalVar({name});
