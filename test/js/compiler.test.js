const {compileExpr, compileNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const runtime = require('../../src/js/runtime');
const {NSEnv, Env} = runtime;
const im = require('immutable');
const {Map, Record} = im;
const vm = require('vm');
const assert = require('assert');
const {fooEnv, flipEnv, flipVar, barNSDecl, barNSEnv} = require('./runtime.test');

function evalCode(code) {
  return new vm.Script(`(function (_runtime, _im) {return ${code};})`).runInThisContext()(runtime, im);
}

function evalNSCode(code, nsEnv, env) {
  return new vm.Script(compileNS(env, nsEnv, code)).runInThisContext()(runtime, im);
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

  it ('loads a defined function', () => {
    const def = compileForm(`(def (flip x y) [y x])`, new NSEnv());
    const expr = compileForm(`(flip 3 4)`, def.nsEnv);
    const result = evalCode(`(function () {${def.code} \n return ${expr.code};})()`);
    assert.deepEqual(result.toJS(), [4, 3]);
  });

  it ('execs a JS global function', () => {
    const expr = evalCode(compileForm(`(js/process.cwd)`).code);
    assert.equal(expr, process.cwd());
  });

  it ('calls a fn referred in from another NS', () => {
    const result = compileForm(`(def flipped (flip 3 4))`, barNSEnv, fooEnv);
    const compiledNS = evalNSCode(result.code, result.nsEnv, fooEnv);

    assert.deepEqual(compiledNS(barNSEnv).exports.get('flipped').value.toJS(), [4, 3]);
  });
});
