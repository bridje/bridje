const {loadFormsAsync, compileForms, evalNodeForms} = require('../../src/js/runtime');
const {Env, NSHeader, NSEnv} = require('../../src/js/env');
const {BoolExpr} = require('../../src/js/expr');
const {readForms} = require('../../src/js/reader');
const {Map, List, Set} = require('immutable');
const fakeNSIO = require('./fake-nsio');
const assert = require('assert');
const fs = require('fs');

describe('runtime', () => {
  async function requireNSAsync(env, ns, fakeNSs) {
    const loadedNSs = await loadFormsAsync(env, ns, {
      nsIO: fakeNSIO(fakeNSs),
      readForms
    });

    return loadedNSs.reduce(({env}, loadedNS) => {
      return evalNodeForms(env, compileForms(env, loadedNS));
    }, {env});
  }

  const kernelBrjAsync = new Promise((resolve, reject) => {
    fs.readFile(require.resolve('../../src/brj/bridje/kernel.brj'), 'utf8', (err, res) => {
      if (err !== null) {
        reject(err);
      } else {
        resolve(res);
      }
    });
  });

  const baseEnvAsync = kernelBrjAsync.then(brj => requireNSAsync(new Env({}), 'bridje.kernel', {'bridje.kernel': {brj}}).then(({env}) => env));

  it ('loads a simple kernel', () => {
    return baseEnvAsync.then(env => {

      const {env: newEnv} = evalNodeForms(env, compileForms(env, Map({
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

    const {env: newEnv} = await requireNSAsync(await baseEnvAsync, 'bridje.kernel.bar', fakeNSs);

    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.bar').exports.get('hello').value.toJS(), [3, 4]);
  });

  it ('uses b.k/compile for non kernel namespaces', async () => {
    const fakeNSs = {
      'bridje.foo': {brj: `(ns bridje.foo) "bell"`}
    };
    const result = await requireNSAsync(await baseEnvAsync, 'bridje.foo', fakeNSs);

    // TODO when `def` works, update this test
  });

  it ('uses a cached ns if possible', async () => {
    const ns = 'bridje.kernel.foo';
    let brj = `(ns ${ns}) (def hello "hello world!")`;

    const {nsCode: compiledFoo} = await requireNSAsync(await baseEnvAsync, ns, {'bridje.kernel.foo': {brj}});

    // we alter it slightly so that we know whether we're using the cached version
    brj = `(ns ${ns}) (def hello "boom!")`;

    const {env} = await requireNSAsync(await baseEnvAsync, ns, {
      'bridje.kernel.foo': {brj, cachedNS: {
        nsHeader: {ns},
        exports: {'hello': {ns, name: 'hello', safeName: 'hello'}},
        nsCode: compiledFoo
      }}
    });

    assert.equal(env.nsEnvs.get('bridje.kernel.foo').exports.get('hello').value, 'hello world!');
  });

  describe ('loadFormsAsync', () => {
    it ('loads an ns from a string', () => {
      const brj = `(ns bridje.kernel.foo) (def hello "hello world!")`;
      const brjFile = '/bridje/kernel/foo.brj';

      return baseEnvAsync.then(
        env => loadFormsAsync(env, {brj, brjFile}, {nsIO: fakeNSIO({}), readForms}).then(
          loadedNSs => {
            assert.equal(loadedNSs.size, 1);
            const {nsHeader, forms} = loadedNSs.first().toObject();

            assert.deepEqual(nsHeader.delete('nsHash').toJS(), {ns: 'bridje.kernel.foo', brjFile, nsHash: null, aliases: {}, refers: {}});
            assert.deepEqual(forms.toJS(), readForms(brj).shift().toJS());
          }));
    });
  });

  it ('has built-in kernel exports', async () => {
    const kernelExports = (await baseEnvAsync).nsEnvs.get('bridje.kernel').exports;
    const boolResult = kernelExports.get('BoolExpr').value({bool: true});
    assert(boolResult instanceof BoolExpr);
    assert.strictEqual(boolResult.bool, true);
  });

  module.exports = {baseEnvAsync};
});
