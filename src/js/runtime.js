const {Record, Map} = require('immutable');

const Env = Record({nsEnvs: Map({})});
const NSEnv = Record({ns: null, exports: Map({})});
const Var = Record({ns: null, name: null, value: undefined, safeName: undefined});

module.exports = {Env, NSEnv, Var};
