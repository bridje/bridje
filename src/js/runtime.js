const {readForms} = require('./reader');
const im = require('immutable');
const {Record, List, Map, Set, fromJS} = im;
var process = require('process');
const e = require('./env');
const f = require('../../src/js/form');
const loc = require('../../src/js/location');
const {Env, NSEnv, NSHeader, Var} = e;
const a = require('./analyser');
const {compileExpr, compileNodeNS, compileWebNS} = require('./compiler');
const vm = require('vm');
const {createHash} = require('crypto');

const brjRequires = {_env: e, _im: im, _form: f, _loc: loc};

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

function parseCachedNS(cachedNS) {
  return fromJS(cachedNS)
    .update('nsHeader', h => new NSHeader(h))
    .update('exports', e => e.map(v => new Var(v)));
}

function nsDependents(nsHeader) {
  return Set(nsHeader.aliases.valueSeq()).union(nsHeader.refers.valueSeq());
}

function loadFormsAsync(env = new Env({}), ns, {nsIO, readForms}) {
  // TODO can see this taking options like whether to resync from the fs, etc
  const preloadedNSs = Set(env.nsEnvs.keySeq());

  function resolveNSAsync(ns, {loadedNSs}) {
    const brjPromise = typeof ns == 'object' ? Promise.resolve({brj: ns.brj, brjFile: ns.brjFile}) : nsIO.resolveNSAsync(ns);
    ns = typeof ns == 'string' ? ns : undefined;

    return brjPromise.then(({brj, brjFile}) => {
      let forms = readForms(brj);
      const nsHeader = a.readNSHeader(ns, brjFile, forms.first());
      return {ns: nsHeader.ns, brj, nsHeader, forms: forms.shift()};
    });
  }

  function loadFormsAsync_({loadedNSs = Map(), nsLoadOrder = List(), queuedNSs}) {
    if (queuedNSs.isEmpty()) {
      return nsLoadOrder.map(ns => loadedNSs.get(ns));
    } else {
      return Promise.all(queuedNSs.map(ns => resolveNSAsync(ns, {loadedNSs})))
        .catch(err => {
          console.log('err', err);
          return Promise.reject(err);
        })
        .then(
          results => results.reduce(
            ({loadedNSs, nsLoadOrder, queuedNSs}, {ns, brj, nsHeader, forms}) => {
              const dependentNSs = nsDependents(nsHeader)
                    .delete('bridje.kernel')
                    .flatten();

              return {
                loadedNSs: loadedNSs.set(ns, Map({ns, brj, nsHeader, forms})),
                nsLoadOrder: nsLoadOrder.unshift(ns),
                queuedNSs: queuedNSs.delete(ns).union(dependentNSs.subtract(queuedNSs, preloadedNSs))
              };
            },
            {queuedNSs: Set(), loadedNSs, nsLoadOrder}))

        .then(loadFormsAsync_);
    }
  }

  function loadCachedNSsAsync(loadedNSs) {
    const nsHashes = {};
    const promises = [];

    loadedNSs.forEach(loadedNS => {
      const {ns, brj, nsHeader, forms} = loadedNS.toObject();
      const nsHash = fromJS({brj, dependentNSs: nsDependents(nsHeader).map(ns => nsHashes[ns] || env.nsEnvs.get(ns).nsHash || undefined)}).hashCode();
      nsHashes[ns] = nsHash;

      promises.push(nsIO.resolveCachedNSAsync(ns, nsHash).then(cachedNS => {
        cachedNS = cachedNS ? parseCachedNS(cachedNS) : undefined;
        return Map({ns, nsHeader: nsHeader.set('nsHash', nsHash), forms, cachedNS});
      }));
    });

    return Promise.all(promises).then(List);
  }

  return loadFormsAsync_({queuedNSs: Set.of(ns)}).then(loadedNSsSeq => {
    if (preloadedNSs.isEmpty() || ns == null) {
      return loadFormsAsync_({queuedNSs: Set.of('bridje.kernel')}).then(coreNSsSeq => coreNSsSeq.concat(loadedNSsSeq));
    } else {
      return loadedNSsSeq;
    }
  }).then(loadCachedNSsAsync);
}

function compileForms(env, loadedNS) {
  const {nsHeader, forms, cachedNS} = loadedNS.toObject();

  function withKernelExports(nsEnv) {
    if (nsEnv.ns == 'bridje.kernel') {
      return nsEnv.set('exports', e.kernelExports);
    } else {
      return nsEnv;
    }
  }

  if (cachedNS) {
    const {nsHeader: cachedNSHeader, exports, nsCode} = cachedNS.toObject();
    return {
      nsHeader: cachedNSHeader, nsCode,
      nsEnv: withKernelExports(a.resolveNSHeader(env, cachedNSHeader).set('exports', exports))
    };
  } else {
    const isKernel = nsHeader.ns == 'bridje.kernel';
    const isKernelNS = isKernel || nsHeader.ns.startsWith('bridje.kernel.');
    const kernelAnalyse = !isKernel ? env.nsEnvs.get('bridje.kernel').exports.get('analyse').value : undefined;

    return forms.reduce(
      ({nsEnv, compiledForms}, form) => {
        const expr = isKernelNS ? a.analyseForm(env, nsEnv, form) : kernelAnalyse(env, nsEnv, form);
        let compiledForm;
        ({nsEnv, compiledForm} = compileExpr(env, nsEnv, expr));
        return {nsHeader, nsEnv, compiledForms: compiledForms.push(compiledForm)};
      },
      {
        nsHeader,
        nsEnv: withKernelExports(a.resolveNSHeader(env, nsHeader)),
        compiledForms: new List()
      });
  }
}

function evalNodeForms(env, {nsHeader, nsEnv, nsCode, compiledForms}) {
  nsCode = nsCode || compileNodeNS(nsEnv, compiledForms);
  const loadNS = evalJS(nsCode)(brjRequires);

  return {env: env.setIn(['nsEnvs', nsEnv.ns], loadNS(nsEnv)), nsCode, nsHeader};
}

function emitWebForms(env, {nsEnv, nsHeader, nsCode, compiledForms}) {
  nsCode = nsCode || compileWebNS(env, nsEnv, compiledForms);

  return {nsHeader, nsEnv, nsCode};
}

module.exports = {EnvQueue, loadFormsAsync, compileForms, evalNodeForms, emitWebForms};
