const {readForms} = require('./reader');
const im = require('immutable');
const {Record, List, Map} = im;
var process = require('process');
const e = require('./env');
const a = require('./analyser');
const c = require('./compiler');
const vm = require('vm');
const runtime = require('./runtime');

module.exports = function(nsLoader) {
  var envManager = e.envManager();

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
        nsEnv: a.resolveNSHeader(env, a.readNSHeader(ns, forms.first())),
        codes: new List()
      });

    const {exports} = new vm.Script(c.compileNS(env, nsEnv, codes.join("\n")))
          .runInThisContext()(runtime, im)(nsEnv);

    return env.setIn(['nsEnvs', ns], nsEnv.set('exports', exports));
  }

  function envRequireAsync(env, ns, str) {
    // TODO require other namespaces as necessary

    if (env.nsEnvs.get(ns) === undefined) {
      const strAsync = str !== undefined ? Promise.resolve(str) : nsLoader.resolveNSAsync(ns);
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
