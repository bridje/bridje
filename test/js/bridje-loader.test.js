const {loadAsync} = require('../../src/js/bridje-loader');
const {Env, NSHeader} = require('../../src/js/env');
const {loadFormsAsync, compileForms, evalNodeForms} = require('../../src/js/runtime');
const {baseEnvAsync} = require('./runtime.test.js');
const {readForms} = require('../../src/js/reader');
const {Map, List, Set} = require('immutable');
const {fakeNSResolver} = require('./fake-nsio');
const assert = require('assert');
const {wrapWebJS} = require('./webpack.test.js');

async function requireWebNSAsync(env, {input, isMainNS}, {requires, filesByExt}) {
  const out = await loadAsync(env, {input, isMainNS}, {
    resolveNSAsync: fakeNSResolver(filesByExt),
    readForms
  });

  return {exports: wrapWebJS(out, requires)};
}

describe ('bridje-loader', () => {
  it ('loads a simple NS', async () => {
    const {exports} = await requireWebNSAsync(await baseEnvAsync, {
      input: `(ns bridje.kernel.foo) (def hello ["hello world"])`,
      isMainNS: false
    }, {requires: Map({}), filesByExt: {}});

    assert.deepEqual(exports.get('hello').value.toJS(), ['hello world']);
  });
});
