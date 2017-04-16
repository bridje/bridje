const Runtime = require('../../src/js/runtime');
const {NSHeader} = require('../../src/js/env');
const {readForms} = require('../../src/js/reader');
const {List, Set} = require('immutable');
const fakeNSIO = require('./fake-nsio');
const nsio = require('../../src/js/nsio');
const realNSIO = nsio({projectPaths: ["src/brj", "test/brj"]});

var assert = require('assert');

describe('runtime', () => {
  it ('loads a simple kernel', () => {
    const runtime = Runtime(fakeNSIO({
      brj: {
        'bridje.kernel': `(ns bridje.kernel)`
      }
    }));

    const {newEnv} = runtime.envRequire(runtime.currentEnv(), NSHeader({ns:'bridje.kernel.test'}), {forms: readForms(`(def hello ["hello world"])`)});
    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('hello').value.toJS(), ['hello world']);
  });

  it ('requires in another namespace', async () => {
    const runtime = Runtime(fakeNSIO({
      brj: {
        'bridje.kernel': `(ns bridje.kernel)`,
        'bridje.kernel.foo': `(ns bridje.kernel.foo) (def (flip x y) [y x])`,
        'bridje.kernel.bar': `(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip]}}) (def hello (flip 4 3))`
      }}));

    const newEnv = await runtime.envRequireAsync(await runtime.coreEnvAsync, 'bridje.kernel.bar');
    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.bar').exports.get('hello').value.toJS(), [3, 4]);
  });

  it ('runs a main', () => {
    Runtime(realNSIO).runMain('bridje.kernel.hello-world');
  });
});
