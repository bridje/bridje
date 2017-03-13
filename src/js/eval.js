var reader = require('./reader');
var path = require('path');
var fs = require('fs');
var process = require('process');

module.exports = function(projectPaths) {
  var envManager = require('./env')();

  function readFileAsync(path) {
    return new Promise((resolve, reject) => {
      fs.readFile(path, 'utf8', (err, res) => {
        if (err !== null) {
          reject(err);
        } else {
          resolve(res);
        }
      });
    });
  }

  function resolveNSAsync(ns) {
    var promise = Promise.reject('No project paths available.');

    var isFileError = err => err.syscall == 'open';
    var isFileNotExistsError = err => err.code == 'ENOENT';

    for (let i = 0; i < projectPaths.length; i++) {
      promise = promise.catch((err) => {
        if (isFileError(err) && !isFileNotExistsError(err)) {
          return Promise.reject(err);
        } else {
          return readFileAsync(path.resolve(process.cwd(), projectPaths[i], ns.replace('.', '/') + '.brj'));
        }
      });
    }

    return promise.catch(err => {
      if (isFileError(err) && isFileNotExistsError(err)) {
        return Promise.reject({
          error: 'ENOENT',
          projectPaths: projectPaths,
          ns: ns
        });
      } else {
        return Promise.reject(err);
      }
    });
  }

  async function envRequireAsync(env, ns, str) {
    if (env.nsEnvs.get(ns) === undefined) {
      if (str === undefined) {
        try {
          str = await resolveNSAsync(ns);
        } catch (e) {
          return Promise.reject(e);
        }
      }

      return env.setIn(['nsEnvs', ns], {ns: ns, str: str});
    } else {
      return env;
    }
  }

  return {
    envRequireAsync,

    loaded: envManager.updateEnv(async (env) => {
      env = await envRequireAsync(env, 'bridje.kernel');
      // env = await envLoadNS(env, 'bridje.core');

      return env;
    })
  };
};
