var e = require('../../src/js/eval')(['src/bridje', 'src/js/test']);

var assert = require('assert');

describe('eval', () => {
  it ('loads a simple kernel', () => {
    const newEnv = e.envRequire(e.currentEnv(), 'bridje.kernel.test', '(ns bridje.kernel.test) (def hello ["hello world"])');

    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('hello').value.toJS(), ['hello world']);
  });

  it ('loads a double hello', () => {
    const newEnv = e.envRequire(e.currentEnv(), 'bridje.kernel.test', '(ns bridje.kernel.test) (def hello "hello") (def double [hello hello])');

    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('double').value.toJS(), ['hello', 'hello']);
  });
});
