const {readForms} = require('./reader');
const im = require('immutable');
const {Record, List, Map, Set} = im;
var process = require('process');
const e = require('./env');
const a = require('./analyser');
const c = require('./compiler');
const vm = require('vm');
const runtime = require('./runtime');

module.exports = function(nsIO) {
  var envManager = e.envManager();

  function envRequire(env, nsHeader, forms) {
    const {nsEnv, codes} = forms.reduce(
      ({nsEnv, codes}, form) => {
        const expr = a.analyseForm(env, nsEnv, form);
        let code;
        ({nsEnv, code} = c.compileExpr(env, nsEnv, expr));
        return {nsEnv, codes: codes.push(code)};
      },
      {
        nsEnv: a.resolveNSHeader(env, nsHeader),
        codes: new List()
      });

    const nsCode = c.compileNS(env, nsEnv, codes.join("\n"));
    const {loadNS} = new vm.Script(nsCode).runInThisContext()(runtime, im);

    return {
      newEnv: env.setIn(['nsEnvs', nsEnv.ns], loadNS(nsEnv)),
      nsCode: nsCode
    };
  }

  function resolveNSsAsync(env, ns, str) {
    const preLoadedNSs = Set(env.nsEnvs.keySeq());

    function readNS(ns, str) {
      const forms = readForms(str);
      const nsHeader = a.readNSHeader(ns, forms.first());
      const dependentNSs = Set(nsHeader.aliases.valueSeq()).union(nsHeader.refers.valueSeq().flatten());
      return {nsHeader, dependentNSs, forms: forms.shift()};
    }

    function resolveQueueAsync({loadedNSs, nsLoadOrder, queuedNSs}) {
      if (queuedNSs.isEmpty()) {
        return nsLoadOrder.map(ns => loadedNSs.get(ns));
      } else {
        return Promise.all(queuedNSs.map(ns => nsIO.resolveNSAsync(ns).then(str => ({ns, str})))).then(results => {
          queuedNSs = Set();
          results.forEach(({ns, str}) => {
            const {nsHeader, dependentNSs, forms} = readNS(ns, str);
            loadedNSs = loadedNSs.set(ns, Map({nsHeader, forms}));
            nsLoadOrder = nsLoadOrder.unshift(ns);
            queuedNSs = queuedNSs.delete(ns).union(dependentNSs.subtract(queuedNSs, preLoadedNSs));
          });

          return resolveQueueAsync({loadedNSs, nsLoadOrder, queuedNSs});
        });
      }
    }

    if (str !== undefined) {
      const {nsHeader, dependentNSs, forms} = readNS(ns, str);
      return resolveQueueAsync({
        loadedNSs: Map({ns: {nsHeader, forms}}),
        nsLoadOrder: List.of(ns),
        queuedNSs: Set.of(dependentNSs)
      });
    } else {
      return resolveQueueAsync({
          loadedNSs: Map(),
          nsLoadOrder: List(),
          queuedNSs: Set.of(ns)
      });
    }
  }

  function envRequireAsync(env, ns, str) {
    if (env.nsEnvs.get(ns) === undefined) {
      return resolveNSsAsync(env, ns, str).then(loadedNSs => {
        return loadedNSs.reduce((envAsync, loadedNS) => {
          return envAsync.then(env => {
            const {nsHeader, forms} = loadedNS.toObject();
            const {newEnv, nsCode} = envRequire(env, nsHeader, forms);
            return nsIO.writeNSAsync(ns, nsCode).then(_ => newEnv);
          });
        }, Promise.resolve(env));
      });
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
