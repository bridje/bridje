const {readForms} = require('./reader');
const im = require('immutable');
const {Record, List, Map, Set} = im;
var process = require('process');
const e = require('./env');
const {Env} = e;
const a = require('./analyser');
const {compileExpr, compileNodeNS, compileWebNS} = require('./compiler');
const vm = require('vm');
const {createHash} = require('crypto');

function EnvQueue() {
  var env = new Env({});

  var envQueue = [];
  var running = false;

  function runQueue() {
    var f = envQueue.shift();
    return f().then(_ => {
      if (envQueue.length > 0) {
        setTimeout(runQueue, 0);
      } else {
        running = false;
      }
    });
  }

  return {
    currentEnv: function() {
      return env;
    },

    updateEnv: function(f) {
      return new Promise(function (resolve, reject) {
        envQueue.push(function() {
          return f(env).then(newEnv => {
            env = newEnv;
            resolve(env);
          }).catch(reject);
        });

        if (!running) {
          running = true;
          runQueue();
        }
      });
    }
  };
};

function evalJS(js) {
  return new vm.Script(js).runInThisContext();
}

function loadFormsAsync(env = new Env({}), ns, {nsResolver, readForms}) {
  // TODO can see this taking options like whether to resync from the fs, etc
  const preloadedNSs = Set(env.nsEnvs.keySeq());

  function resolveNSAsync(ns) {
    const brjPromise = typeof ns == 'object' ? Promise.resolve({brj: ns.brj, brjFile: ns.brjFile}) : nsResolver.resolveNSAsync(ns);
    ns = typeof ns == 'string' ? ns : undefined;

    return brjPromise.then(({brj, brjFile}) => {
      const forms = readForms(brj);
      const nsHeader = a.readNSHeader(ns, brjFile, forms.first());
      ns = nsHeader.ns;

      return nsResolver.resolveCachedNSAsync(ns).then(cachedNS => ({ns, nsHeader, forms, cachedNS}));
    });
  }

  function loadFormsAsync_({loadedNSs = Map(), nsLoadOrder = List(), queuedNSs}) {
    if (queuedNSs.isEmpty()) {
      return nsLoadOrder.map(ns => loadedNSs.get(ns));
    } else {
      return Promise.all(queuedNSs.map(ns => resolveNSAsync(ns)))
        .catch(err => {
          console.log('err', err);
          return Promise.reject(err);
        })
        .then(
          results => results.reduce(
            ({loadedNSs, nsLoadOrder, queuedNSs}, {ns, nsHeader, forms, cachedNS}) => {
              const dependentNSs = Set(nsHeader.aliases.valueSeq())
                    .union(nsHeader.refers.valueSeq())
                    .delete('bridje.kernel')
                    .flatten();

              return {
                loadedNSs: loadedNSs.set(ns, Map({nsHeader, forms: forms.shift()})),
                nsLoadOrder: nsLoadOrder.unshift(ns),
                queuedNSs: queuedNSs.delete(ns).union(dependentNSs.subtract(queuedNSs, preloadedNSs))
              };
            },
            {queuedNSs: Set(), loadedNSs, nsLoadOrder}))

        .then(loadFormsAsync_);
    }
  }

  return loadFormsAsync_({queuedNSs: Set.of(ns)}).then(loadedNSsSeq => {
    if (preloadedNSs.isEmpty() || ns == null) {
      return loadFormsAsync_({queuedNSs: Set.of('bridje.kernel')}).then(coreNSsSeq => coreNSsSeq.concat(loadedNSsSeq));
    } else {
      return loadedNSsSeq;
    }
  });
}

function compileForms(env, loadedNS) {
  const {nsHeader, forms} = loadedNS.toObject();
  return forms.reduce(
    ({nsEnv, compiledForms}, form) => {
      const expr = a.analyseForm(env, nsEnv, form);
      let compiledForm;
      ({nsEnv, compiledForm} = compileExpr(env, nsEnv, expr));
      return {nsEnv, compiledForms: compiledForms.push(compiledForm)};
    },
    {
      nsEnv: a.resolveNSHeader(env, nsHeader),
      compiledForms: new List()
    });
}

function evalNodeForms(env, {nsEnv, compiledForms}) {
  const nsCode = compileNodeNS(nsEnv, compiledForms);
  const loadNS = evalJS(nsCode)(e, im);

  return env.setIn(['nsEnvs', nsEnv.ns], loadNS(nsEnv));
}

function emitWebForms(env, {nsEnv, compiledForms}) {
  // TODO probably loads more to do here
  return compileWebNS(env, nsEnv, compiledForms);
}

module.exports = {EnvQueue, loadFormsAsync, compileForms, evalNodeForms, emitWebForms};
