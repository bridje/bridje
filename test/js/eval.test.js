var e = require('../../src/js/eval')(['src/bridje', 'src/js/test']);

var assert = require('assert');

function assertEvalResult(code, expected, decls = '') {
  const newEnv = e.envRequire(e.currentEnv(), 'bridje.kernel.test', `(ns bridje.kernel.test) ${decls} (def val ${code})`);

  assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('val').value.toJS(), expected);
}

describe('eval', () => {
  it ('loads a simple kernel', () => {
    assertEvalResult('["hello world"]', ['hello world']);
  });

  it ('loads a double hello', () => {
    assertEvalResult('[hello hello]', ['hello', 'hello'], '(def hello "hello")');
  });

  it ('loads a let', () => {
    assertEvalResult('(let [x 1, y 2] [x y])', [1, 2]);
  });

});
