const {loadFormsAsync, compileForms, evalNodeForms, emitWebForms} = require('./runtime');
const {readForms} = require('./reader');
const {readNSHeader} = require('./analyser');
const {Env} = require('./env');
const {nsResolver} = require('./nsio');
const {List, Map} = require('immutable');
const path = require('path');

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

  const done = this.async();

  loadFormsAsync(env, {brj: input}, {
    resolveNSAsync: nsResolver(projectPaths),
    readForms
  }).then(loadedNSs => {
    // TODO load more than one NS
    let out = emitWebForms(env, compileForms(env, loadedNSs.last()));

    if (isMainNS.call(this)) {
      out += `main()`;
    }

    done(null, out);
  });

};
