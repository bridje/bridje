var reader = require('./reader');
var path = require('path');
var fs = require('fs');

// TODO this needs parameters to tell it where the source is
module.exports = function() {
  var envManager = require('./env')();

  async function envRequire(env, ns, str) {
    if (env.nsEnvs.get(ns) === undefined) {
      // TODO actually require in the env
      return env.setIn(['nsEnvs', ns], {ns: ns});
    } else {
      return env;
    }
  }

  return {
    envRequire: envRequire,

    loaded: envManager.updateEnv(async (env) => {
      env = await envRequire(env, 'bridje.kernel');
      // env = await envLoadNS(env, 'bridje.core');

      return env;
    })
  };
};
