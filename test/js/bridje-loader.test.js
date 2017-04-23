const {loadAsync} = require('../../src/js/bridje-loader');
const {Env, NSHeader} = require('../../src/js/env');
const {loadFormsAsync, compileForms, evalNodeForms} = require('../../src/js/runtime');
const {baseEnvAsync} = require('./runtime.test.js');
const {readForms} = require('../../src/js/reader');
const {Map, List, Set} = require('immutable');
const {fakeNSResolver} = require('./fake-nsio');
const assert = require('assert');
const {wrapWebJS} = require('./webpack.test.js');
const {flipVar} = require('./env.test.js');

async function requireWebNSAsync(env, {brj, brjFile, isMainNS}, {requires, fakeNSs}) {
  const {nsHeader, nsCode} = await loadAsync(env, {brj, brjFile, isMainNS}, {
    nsResolver: fakeNSResolver(fakeNSs),
    readForms
  });

  return {nsHeader, nsCode, exports: wrapWebJS(nsCode, requires)};
}

describe ('bridje-loader', () => {
  const fooCompileResultAsync = baseEnvAsync.then(env => requireWebNSAsync(env, {
    brj: `(ns bridje.kernel.foo) (def hello ["hello world"])`,
    brjFile: '/bridje/kernel/foo.brj',
    isMainNS: false
  }, {requires: Map({}), fakeNSs: {}}));

  it ('loads a simple NS', async () => {
    const {exports} = await fooCompileResultAsync;

    assert.deepEqual(exports.get('hello').value.toJS(), ['hello world']);
  });

  it ('loads multiple namespaces', async () => {
    const {exports} = await requireWebNSAsync(await baseEnvAsync, {
      brj: `(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip]}}) (def flipped (flip "world" "hello"))`,
      brjFile: '/bridje/kernel/bar.brj',
      isMainNS: false
    }, {
      requires: Map({
        '/bridje/kernel/foo.brj': Map({flip: flipVar})
      }),
      fakeNSs: {
        'bridje.kernel.foo': {
          brj: `(ns bridje.kernel.foo) (def (flip x y) [y x])`
        }
      }});

    assert.deepEqual(exports.get('flipped').value.toJS(), ['hello', 'world']);
  });

  it ('uses cached nsCode if possible', async () => {
    const {nsHeader, nsCode} = await fooCompileResultAsync;

    const {exports} = await requireWebNSAsync(await baseEnvAsync, {
      brj: `(ns bridje.kernel.foo) (def hello ["boom!"])`,
      brjFile: '/bridje/kernel/foo.brj',
      isMainNS: false
    }, {requires: Map({}), fakeNSs: {'bridje.kernel.foo': {
      brj: `(ns bridje.kernel.foo) (def hello ["hello world"])`,
      cachedNS: {nsHeader, nsCode, exports: {'hello': {ns: 'bridje.kernel.foo', name: 'hello', safeName: 'hello'}}}
    }}});

    assert.deepEqual(exports.get('hello').value.toJS(), ['hello world']);
  });
});
