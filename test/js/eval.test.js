var e = require('../../src/js/eval')();

var assert = require('assert');

describe('eval', () => {
  it ('loads the kernel', async () => {
    var env = await e.loaded;
    assert(env.nsEnvs.get('bridje.kernel') !== undefined);
  });

});
