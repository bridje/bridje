const im = require('immutable');
const brjEnv = require('../../src/js/env');
const fakeNSIO = require('./fake-nsio');
const babel = require('babel-core');
const vm = require('vm');

function wrapWebJS(code, requires) {
  code = babel.transform(code, {plugins: ["transform-es2015-modules-commonjs"]}).code;
  const f = new vm.Script(`(function (require, exports) {${code}; return exports.default;})`).runInThisContext();
  requires = requires.set('immutable', im).set('../../../../src/js/env', brjEnv);
  return f(req => requires.get(req), {});
}

module.exports = {wrapWebJS};
