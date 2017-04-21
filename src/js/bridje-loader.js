const {loadFormsAsync, compileForms, evalNodeForms, emitWebForms} = require('./runtime');
const {readForms} = require('./reader');
const {readNSHeader} = require('./analyser');
const {Env} = require('./env');
const {nsResolver} = require('./nsio');
const {List, Map} = require('immutable');
const path = require('path');

module.exports = function(input) {
  // TODO creating a new env every time. VERY BAD.
  const env = new Env({});

  // TODO got to call through to loadFormsAsync to pull in other namespaces
  const forms = readForms(input);

  const nsHeader = readNSHeader(undefined, forms.first());
  let out = emitWebForms(env, compileForms(env, Map({nsHeader, forms: forms.shift()})));

  const entryPoints = typeof this.options.entry == 'string' ? [this.options.entry] : this.options.entry;

  for (const entryPoint in entryPoints) {
    if (this.resourcePath == path.resolve(this.options.context, entryPoints[entryPoint])) {
      out += `main()`;
    }
  }

  return out;
};
