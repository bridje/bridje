var e = require('../../src/js/eval')(['src/bridje', 'src/js/test']);

var assert = require('assert');

describe('eval', () => {
  it ('loads a simple kernel', () => {
    console.log(e.envRequire(e.currentEnv(), 'bridje.kernel', '(ns bridje.kernel) (def hello ["hello world"])'));
  });

  // it ('loads the kernel', async () => {
  //   var env = await e.loaded;
  //   console.log(env.toJS());
  //   assert(env.nsEnvs.get('bridje.kernel') !== undefined);
  // });

});
