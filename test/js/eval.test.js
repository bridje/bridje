var e = require('../../src/js/eval');
const fakeLoader = require('./fake-ns-loader');
const realLoader = require('../../src/js/ns-loader')(["src/brj", "test/brj"]);

var assert = require('assert');

describe('eval', () => {
  it ('loads a simple kernel', () => {
    const eval = e(fakeLoader({
      'bridje.kernel': `(ns bridje.kernel)`
    }));

    const newEnv = eval.envRequire(eval.currentEnv(), 'bridje.kernel.test', `(ns bridje.kernel.test) (def hello ["hello world"])`);
    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('hello').value.toJS(), ['hello world']);
  });

  it ('runs a main', async () => {
    console.log(await e(realLoader).runMain('bridje.kernel.hello-world'));
  });
});
