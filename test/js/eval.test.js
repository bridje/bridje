var e = require('../../src/js/eval')(['src/bridje', 'src/js/test']);

var assert = require('assert');

describe('eval', () => {
  it ('loads the kernel', async () => {
    var env = await e.loaded;
    console.log(env.toJS());
    assert(env.nsEnvs.get('bridje.kernel') !== undefined);
  });

});
