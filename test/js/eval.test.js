var e = require('../../src/js/eval')(['src/brj', 'src/js/test', 'test/brj']);

var assert = require('assert');

describe('eval', () => {
  it ('loads a simple kernel', () => {
    const newEnv = e.envRequire(e.currentEnv(), 'bridje.kernel.test', `(ns bridje.kernel.test) (def hello ["hello world"])`);
    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('hello').value.toJS(), ['hello world']);
  });

  it ('runs a main', async () => {
    console.log(await e.runMain('bridje.kernel.hello-world'));
  });
});
