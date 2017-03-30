const {Record, Map} = require('immutable');

const Env = Record({nsEnvs: Map({})});
const NSEnv = Record({ns: null, exports: Map({})});
const Var = Record({value: null});

module.exports = {Env, NSEnv, Var};
