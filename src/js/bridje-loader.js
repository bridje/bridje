const {loadFormsAsync, compileForms, evalNodeForms, emitWebForms} = require('./runtime');
const {readForms} = require('./reader');
const {Env} = require('./env');
const nsIO = require('./nsio');
const {List, Set, Map} = require('immutable');
const path = require('path');

function loadAsync(env, {brj, brjFile, isMainNS}, {nsIO}) {
  return loadFormsAsync(env, {brj, brjFile}, {nsIO, readForms}).then(
    loadedNSs => {
      let nsEnv, nsCode, nsHeader;

      ({env, nsEnv, nsCode, nsHeader} = loadedNSs.reduce(({env}, loadedNS) => {
        const {nsEnv, nsCode, nsHeader} = emitWebForms(env, compileForms(env, loadedNS));
        return {
          env: env.setIn(['nsEnvs', nsEnv.ns], nsEnv),
          nsCode, nsHeader, nsEnv
        };
      }, {env}));

      if (isMainNS) {
        nsCode += `main()`;
      }

      return {nsCode, nsHeader, nsEnv, env};
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
    nsIO: nsIO(projectPaths)
  }).then(({nsCode, nsEnv, env}) => {
    Set(nsEnv.refers.valueSeq().map(r => r.ns)).union(Set(nsEnv.aliases.valueSeq()).map(a => a.ns))
      .forEach(ns => this.addDependency(env.nsEnvs.get(ns).brjFile));
    done(null, nsCode);
  });
};

module.exports.loadAsync = loadAsync;
