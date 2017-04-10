var e = require('../../src/js/eval');
var {NSHeader} = require('../../src/js/runtime');
var {readForms} = require('../../src/js/reader');
const fakeNSIO = require('./fake-nsio');
const realNSIO = require('../../src/js/nsio')(["src/brj", "test/brj"]);

var assert = require('assert');

describe('eval', () => {
  it ('loads a simple kernel', () => {
    const eval = e(fakeNSIO({
      'bridje.kernel': `(ns bridje.kernel)`
    }));

    const {newEnv} = eval.envRequire(eval.currentEnv(), NSHeader({ns:'bridje.kernel.test'}), readForms(`(def hello ["hello world"])`));
    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('hello').value.toJS(), ['hello world']);
  });

  it ('requires in another namespace', async () => {
    const eval = e(fakeNSIO({
      'bridje.kernel': `(ns bridje.kernel)`,
      'bridje.kernel.foo': `(ns bridje.kernel.foo) (def (flip x y) [y x])`,
      'bridje.kernel.bar': `(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip]}}) (def hello (flip 4 3))`
    }));

    const newEnv = await eval.envRequireAsync(await eval.loaded, 'bridje.kernel.bar');
    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.bar').exports.get('hello').value.toJS(), [3, 4]);
  });

  it ('runs a main', () => {
    e(realNSIO).runMain('bridje.kernel.hello-world');
  });
});
