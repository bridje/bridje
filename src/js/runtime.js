const {readForms} = require('./reader');
const im = require('immutable');
const {Record, List, Map, Set} = im;
var process = require('process');
const e = require('./env');
const {Env} = e;
const a = require('./analyser');
const c = require('./compiler');
const vm = require('vm');
const {createHash} = require('crypto');

function envQueue() {
  var env = new Env({});

  var envQueue = [];
  var running = false;

  async function runQueue() {
    var f = envQueue.shift();
    await f();

    if (envQueue.length > 0) {
      setTimeout(runQueue, 0);
    } else {
      running = false;
    }
  }

  return {
    currentEnv: function() {
      return env;
    },

    updateEnv: function(f) {
      return new Promise(function (resolve, reject) {
        envQueue.push(async function() {
          env = await f(env);
          resolve(env);
        });

        if (!running) {
          running = true;
          runQueue();
        }
      });
    }
  };
};

module.exports = function(nsIO) {
  var queue = envQueue();

  function envRequire(env, nsHeader, {hash, forms}) {
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

    const nsCode = c.compileNS(env, nsEnv, {hash, code: codes.join("\n")});
    const {loadNS} = new vm.Script(nsCode).runInThisContext()(e, im);

    return {
      nsCode, newEnv: env.setIn(['nsEnvs', nsEnv.ns], loadNS(nsEnv))
    };
  }

  function envLoadJS(env, nsHeader, loadNS) {
    return {newEnv: env.setIn(['nsEnvs', nsHeader.ns], loadNS(a.resolveNSHeader(env, nsHeader)))};
  }

  function resolveNSsAsync(env, ns, str) {
    const preLoadedNSs = Set(env.nsEnvs.keySeq());

    function hashNS(str) {
      const hasher = createHash('sha1');
      hasher.update(str);
      return hasher.digest('hex');
    }

    function readNS(ns, brj) {
      const forms = readForms(brj);
      const nsHeader = a.readNSHeader(ns, forms.first());
      return {nsHeader, hash: hashNS(brj), forms: forms.shift()};
    }

    function nsDependents(nsHeader) {
      return Set(nsHeader.aliases.valueSeq()).union(nsHeader.refers.valueSeq().flatten());
    }

    function resolveNSAsync(ns) {
      return Promise.all([nsIO.resolveNSAsync(ns, 'brj').catch(e => null), nsIO.resolveNSAsync(ns, 'js').catch(e => null)])
        .then(([brj, js]) => {
          if (brj == null && js == null) {
            return Promise.reject(`Can't find namespace '${ns}'`);
          } else {
            return {ns, brj, js};
          }
        });
    }

    function chooseNSInputAsync({ns, brj, js}) {
      if (js) {
        const {nsHeader, hash, loadNS} = eval(js)(e, im);
        if (nsHeader && hash && loadNS) {
          if (!brj || hashNS(brj) == hash) {
            return {ns, nsHeader, loadNS};
          }
        } else {
          return Promise.reject(`Malformed JS for namespace '${ns}'`);
        }
      }

      const {nsHeader, hash, forms} = readNS(ns, brj);
      return {ns, nsHeader, hash, forms};
    }

    function resolveQueueAsync({loadedNSs, nsLoadOrder, queuedNSs}) {
      if (queuedNSs.isEmpty()) {
        return nsLoadOrder.map(ns => loadedNSs.get(ns));
      } else {
        return Promise.all(queuedNSs.map(ns => resolveNSAsync(ns).then(chooseNSInputAsync))).then(results => {
          queuedNSs = Set();
          results.forEach(({ns, nsHeader, hash, forms, loadNS}) => {
            const dependentNSs = nsDependents(nsHeader);
            loadedNSs = loadedNSs.set(ns, Map({nsHeader, loadNS, hash, forms}));
            nsLoadOrder = nsLoadOrder.unshift(ns);
            queuedNSs = queuedNSs.delete(ns).union(dependentNSs.subtract(queuedNSs, preLoadedNSs));
          });

          return resolveQueueAsync({loadedNSs, nsLoadOrder, queuedNSs});
        });
      }
    }

    if (str !== undefined) {
      const {nsHeader, dependentNSs, hash, forms} = readNS(ns, str);
      return resolveQueueAsync({
        loadedNSs: Map({ns: {nsHeader, hash, forms}}),
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
      return resolveNSsAsync(env, ns, str).then(
        loadedNSs => loadedNSs.reduce(
          (envAsync, loadedNS) => envAsync.then(
            env => {
              const {nsHeader, loadNS, hash, forms} = loadedNS.toObject();
              const {newEnv, nsCode} = loadNS ? envLoadJS(env, nsHeader, loadNS) : envRequire(env, nsHeader, {hash, forms});

              if (nsCode !== undefined) {
                return nsIO.writeNSAsync(ns, nsCode).then(_ => newEnv);
              } else {
                return Promise.resolve(newEnv);
              }
            }),

          Promise.resolve(env)));
    } else {
      return Promise.resolve(env);
    }
  }

  const coreEnvAsync = queue.updateEnv(env => envRequireAsync(env, 'bridje.kernel'));

  function runMain(ns, argv) {
    queue.updateEnv(env => envRequireAsync(env, ns).then(
      env => {
        const mainFn = env.getIn(['nsEnvs', ns, 'exports', 'main', 'value']);
        mainFn(argv);

        // TODO we have to run an IO action if it's here, even though kernel
        // shouldn't really know about IO

        return env;
      }).catch (e => console.log(e)));
  }

  function build(entryNSs) {
    return entryNSs.reduce((envAsync, entryNS) => envAsync.then(env => envRequireAsync(env, entryNS)), coreEnvAsync);
  }

  function currentEnv() {
    return queue.currentEnv();
  }

  return {envRequireAsync, envRequire, currentEnv, coreEnvAsync, runMain, build};
};
