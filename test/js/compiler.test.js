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
  it('loads a vector', () => {
    const result = evalCode(compileForm('["hello world!"]').code);
    assert.deepEqual(result.toJS(), ['hello world!']);
  });

  it('loads a record', () => {
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

  it ('loads a fn', () => {
    const expr = evalCode(compileForm(`(fn (x y) [y x])`).code);
    assert.deepEqual(expr(3, 4).toJS(), [4, 3]);
  });

  it ('loads a call', () => {
    const expr = evalCode(compileForm(`((fn (x y) [y x]) 3 4)`).code);
    assert.deepEqual(expr.toJS(), [4, 3]);
  });
});
