const {Record, Map} = require('immutable');

const Env = Record({nsEnvs: Map({})});
const NSEnv = Record({ns: null, exports: Map({})});
const Var = Record({value: undefined, safeName: undefined});

module.exports = {Env, NSEnv, Var};
