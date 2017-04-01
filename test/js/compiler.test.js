const {compileExpr, compileNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const runtime = require('../../src/js/runtime');
const {NSEnv, Env} = runtime;
const im = require('immutable');
const {Record} = im;
const vm = require('vm');
const assert = require('assert');

function evalCode(code) {
  return new vm.Script(`(function (_runtime, _im) {return ${code};})`).runInThisContext()(runtime, im);
}

function compileForm(form, nsEnv, env) {
  return compileExpr(env, nsEnv, analyseForm(env, nsEnv, readForms(form).first()));
}

describe('compiler', () => {
  it('compiles a NS', () => {
    let expr = analyseForm(null, null, readForms('(def foo-bar ["hello world!"])').first());
    let compileResult = compileExpr(null, new runtime.NSEnv({}), expr);
    let compiledNS = new vm.Script(compileNS(null, compileResult.nsEnv, compileResult.code)).runInThisContext()(runtime, im).f();
    assert.deepEqual(compiledNS.exports.getIn(['foo-bar', 'value']).toJS(), ['hello world!']);
  });

  it('compiles a record', () => {
    const result = evalCode(compileForm('{a 1, b 2}').code);
    assert.deepEqual(result.toJS(), {a: 1, b: 2});
 });

  it ('loads a let', () => {
    const result = evalCode(compileForm(`(let [x 1, y 2] [x y])`).code);
    assert.deepEqual(result.toJS(), [1, 2]);
  });

  it ('loads a double hello', () => {
    const r0 = compileForm(`(def hello "hello")`, new NSEnv());
    const r1 = compileForm(`(def double [hello hello])`, r0.nsEnv);
    const result = evalCode(`(function () {${r0.code}\n${r1.code} \n return double;})()`);
    assert.deepEqual(result.toJS(), ['hello', 'hello']);
  });
});
