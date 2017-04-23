const {loadFormsAsync, compileForms, evalNodeForms} = require('../../src/js/runtime');
const {Env, NSHeader} = require('../../src/js/env');
const {readForms} = require('../../src/js/reader');
const {Map, List, Set} = require('immutable');
const {fakeNSResolver} = require('./fake-nsio');
const assert = require('assert');

describe('runtime', () => {
  function requireNSAsync(env, ns, fakeNSs) {
    return loadFormsAsync(env, ns, {
      nsResolver: fakeNSResolver(fakeNSs),
      readForms
    }).then(loadedNSs => loadedNSs.reduce((env, loadedNS) => evalNodeForms(env, compileForms(env, loadedNS)), new Env({})));
  }

  const baseEnvAsync = requireNSAsync(undefined, 'bridje.kernel', {'bridje.kernel': {brj: `(ns bridje.kernel)`}});

  it ('loads a simple kernel', () => {
    return baseEnvAsync.then(env => {

      const newEnv = evalNodeForms(env, compileForms(env, Map({
        nsHeader: NSHeader({ns:'bridje.kernel.test'}),
        forms: readForms(`(def hello ["hello world"])`)
      })));

      assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('hello').value.toJS(), ['hello world']);
    });
  });

  it ('requires in another namespace', async () => {
    const fakeNSs = {
      'bridje.kernel': {brj: `(ns bridje.kernel)`},
      'bridje.kernel.foo': {brj: `(ns bridje.kernel.foo) (def (flip x y) [y x])`},
      'bridje.kernel.bar': {brj: `(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip]}}) (def hello (flip 4 3))`}
    };

    const newEnv = await requireNSAsync(await baseEnvAsync, 'bridje.kernel.bar', fakeNSs);

    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.bar').exports.get('hello').value.toJS(), [3, 4]);
  });

  describe ('loadFormsAsync', () => {
    it ('loads an ns from a string', () => {
      const brj = `(ns bridje.kernel.foo) (def hello "hello world!")`;
      const brjFile = '/bridje/kernel/foo.brj';

      return baseEnvAsync.then(
        env => loadFormsAsync(env, {brj, brjFile}, {nsResolver: fakeNSResolver({}), readForms}).then(
          loadedNSs => {
            assert.equal(loadedNSs.size, 1);
            const {nsHeader, forms} = loadedNSs.first().toObject();

            assert.deepEqual(nsHeader.toJS(), {ns: 'bridje.kernel.foo', brjFile, aliases: {}, refers: {}});
            assert.deepEqual(forms.toJS(), readForms(brj).shift().toJS());
          }));
    });
  });

  module.exports = {baseEnvAsync};
});
