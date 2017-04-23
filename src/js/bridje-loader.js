const {loadFormsAsync, compileForms, evalNodeForms, emitWebForms} = require('./runtime');
const {readForms} = require('./reader');
const {Env} = require('./env');
const {nsResolver} = require('./nsio');
const {List, Set, Map} = require('immutable');
const path = require('path');

function loadAsync(env, {brj, brjFile, isMainNS}, {nsResolver}) {
  return loadFormsAsync(env, {brj, brjFile}, {nsResolver, readForms}).then(
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

      let out = emitWebForms(env, {nsEnv, compiledForms});

      if (isMainNS) {
        out += `main()`;
      }

      return {out, nsEnv, env};
    });
}

function isMainNS() {
  const entryPoints = typeof this.options.entry == 'string' ? [this.options.entry] : this.options.entry;

  for (const entryPoint of entryPoints) {
    if (this.resourcePath == path.resolve(this.options.context, entryPoint)) {
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
    nsResolver: nsResolver(projectPaths)
  }).then(({out, nsEnv, env}) => {
    Set(nsEnv.refers.valueSeq().map(r => r.ns)).union(Set(nsEnv.aliases.valueSeq()).map(a => a.ns))
      .forEach(ns => this.addDependency(env.nsEnvs.get(ns).brjFile));
    done(null, out);
  });
};

module.exports.loadAsync = loadAsync;
