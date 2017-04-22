const {loadFormsAsync, compileForms, evalNodeForms, emitWebForms} = require('./runtime');
const {readForms} = require('./reader');
const {Env} = require('./env');
const {nsResolver} = require('./nsio');
const {List, Set, Map} = require('immutable');
const path = require('path');

function loadAsync(env, {brj, brjFile, isMainNS}, {resolveNSAsync}) {
  return loadFormsAsync(env, {brj, brjFile}, {resolveNSAsync, readForms}).then(
    loadedNSs => {
      let nsEnv, compiledForms;
      ({env, nsEnv, compiledForms} = loadedNSs.reduce(({env}, loadedNS) => {
        const {nsEnv, compiledForms} = compileForms(env, loadedNS);
        return {
          env: env.setIn(['nsEnvs', nsEnv.ns], nsEnv),
          nsEnv,
          compiledForms
        };
      }, {env}));

      // TODO load more than one NS
      let out = emitWebForms(env, {nsEnv, compiledForms});

      if (isMainNS) {
        out += `main()`;
      }

      return {out, nsEnv, env};
    });
}

function isMainNS() {
  const entryPoints = typeof this.options.entry == 'string' ? [this.options.entry] : this.options.entry;

  for (const entryPoint in entryPoints) {
    if (this.resourcePath == path.resolve(this.options.context, entryPoints[entryPoint])) {
      return true;
    }
  }

  return false;
}

module.exports = function(input) {
  // TODO creating a new env every time. VERY BAD.
  const env = new Env({});

  const {projectPaths} = this.loaders[this.loaderIndex].options;

  this.cacheable();
  const done = this.async();

  return loadAsync(env, {brj: input, brjFile: this.resourcePath, isMainNS: isMainNS.call(this)}, {
    resolveNSAsync: nsResolver(projectPaths)
  }).then(({out, nsEnv, env}) => {
    Set(nsEnv.refers.valueSeq().map(r => r.ns)).union(Set(nsEnv.aliases.valueSeq()).map(a => a.ns))
      .forEach(ns => this.addDependency(env.nsEnvs.get(ns).brjFile));
    done(null, out);
  });
};

module.exports.loadAsync = loadAsync;
