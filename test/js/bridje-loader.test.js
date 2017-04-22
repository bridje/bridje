const {loadAsync} = require('../../src/js/bridje-loader');
const brjEnv = require('../../src/js/env');
const {Env, NSHeader} = brjEnv;
const {loadFormsAsync, compileForms, evalNodeForms} = require('../../src/js/runtime');
const {baseEnvAsync} = require('./runtime.test.js');
const {readForms} = require('../../src/js/reader');
const im = require('immutable');
const {Map, List, Set} = im;
const {fakeNSResolver} = require('./fake-nsio');
const babel = require('babel-core');
const vm = require('vm');
const assert = require('assert');

function wrapWebJS(code, requires) {
  code = babel.transform(code, {plugins: ["transform-es2015-modules-commonjs"]}).code;
  const f = new vm.Script(`(function (require, exports) {${code}; return exports;})`).runInThisContext();
  return f(req => requires.get(req), {});
}

async function requireWebNSAsync(env, {input, isMainNS}, {requires, filesByExt}) {
  const out = await loadAsync(env, {input, isMainNS}, {
    resolveNSAsync: fakeNSResolver(filesByExt),
    readForms
  });

  return {exports: wrapWebJS(out, requires.set('immutable', im).set('../../../../src/js/env', brjEnv)).default};
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
