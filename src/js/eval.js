const {readForms} = require('./reader');
const im = require('immutable');
const {Record, List} = im;
var path = require('path');
var fs = require('fs');
var process = require('process');
const e = require('./env');
const a = require('./analyser');
const c = require('./compiler');
const vm = require('vm');
const runtime = require('./runtime');

module.exports = function(projectPaths) {
  var envManager = e.envManager();

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

  /// returns Promise<String>
  function resolveNSAsync(ns) {
    var promise = Promise.reject('No project paths available.');

    var isFileError = err => err.syscall == 'open';
    var isFileNotExistsError = err => err.code == 'ENOENT';

    for (let i = 0; i < projectPaths.length; i++) {
      promise = promise.catch((err) => {
        if (isFileError(err) && !isFileNotExistsError(err)) {
          return Promise.reject(err);
        } else {
          return readFileAsync(path.resolve(process.cwd(), projectPaths[i], ns.replace(/\./g, '/') + '.brj'));
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

  function envRequire(env, ns, str) {
    const forms = readForms(str);
    const {nsEnv, codes} = forms.shift().reduce(
      ({nsEnv, codes}, form) => {
        const expr = a.analyseForm(env, nsEnv, form);
        let code;
        ({nsEnv, code} = c.compileExpr(env, nsEnv, expr));
        return {nsEnv, codes: codes.push(code)};
      },
      {
        nsEnv: a.analyseNSForm(env, ns, forms.first()),
        codes: new List()
      });

    const {exports} = new vm.Script(c.compileNS(env, nsEnv, codes.join("\n")))
          .runInThisContext()(runtime, im).f();

    return env.setIn(['nsEnvs', ns], nsEnv.set('exports', exports));
  }

  function envRequireAsync(env, ns, str) {
    // TODO require other namespaces as necessary

    if (env.nsEnvs.get(ns) === undefined) {
      const strAsync = str !== undefined ? Promise.resolve(str) : resolveNSAsync(ns);
      return strAsync.then(str => envRequire(env, ns, str));
    } else {
      return Promise.resolve(env);
    }
  }

  function runMain(ns, argv) {
    envManager.updateEnv(env => envRequireAsync(env, ns).then(
      env => {
        const mainFn = env.getIn(['nsEnvs', ns, 'exports', 'main', 'value']);
        mainFn(argv);

        // TODO we have to run an IO action if it's here, even though kernel
        // shouldn't really know about IO

        return env;
      }).catch (e => console.log(e)));
  }

  function currentEnv() {
    return envManager.currentEnv();
  }

  return {
    envRequireAsync, envRequire, currentEnv, runMain,

    loaded: envManager.updateEnv(async (env) => {
      env = await envRequireAsync(env, 'bridje.kernel');
      // env = await envLoadNS(env, 'bridje.core');

      return env;
    })
  };
};
