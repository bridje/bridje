const {compileExpr, compileNodeNS, compileWebNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const e = require('../../src/js/env');
const {NSEnv, Env} = e;
const im = require('immutable');
const {List, Map, Record} = im;
const vm = require('vm');
const assert = require('assert');
const {fooEnv, flipEnv, flipVar, barNSDecl, barNSEnv} = require('./env.test');

function evalCompiledForm(compiledForm) {
  return new vm.Script(`(function (_env, _im) {return ${compiledForm};})`).runInThisContext()(e, im);
}

function evalNSCode(compiledForms, nsEnv) {
  return new vm.Script(compileNodeNS(nsEnv, compiledForms)).runInThisContext()(e, im);
}

function compileForm(form, nsEnv, env) {
  return compileExpr(env, nsEnv, analyseForm(env, nsEnv, readForms(form).first()));
}

describe('compiler', () => {
  it('loads a vector', () => {
    const result = evalCompiledForm(compileForm('["hello world!"]').compiledForm);
    assert.deepEqual(result.toJS(), ['hello world!']);
  });

  it('loads a record', () => {
    const result = compileForm('(def record {a 1, b 2})', barNSEnv);
    const loadNS = evalNSCode(List.of(result.compiledForm), result.nsEnv);
    assert.deepEqual(loadNS(result.nsEnv).exports.get('record').value.toJS(), {a: 1, b: 2});
  });

  it ('loads a let', () => {
    const result = evalCompiledForm(compileForm(`(let [x 1, y 2] [x y])`).compiledForm);
    assert.deepEqual(result.toJS(), [1, 2]);
  });

  it ('loads a double hello', () => {
    const r0 = compileForm(`(def hello "hello")`, new NSEnv());
    const r1 = compileForm(`(def double [hello hello])`, r0.nsEnv);
    const result = evalCompiledForm(`(function () {${r0.compiledForm}\n${r1.compiledForm} \n return double;})()`);
    assert.deepEqual(result.toJS(), ['hello', 'hello']);
  });

  it ('loads a fn', () => {
    const expr = evalCompiledForm(compileForm(`(fn (x y) [y x])`).compiledForm);
    assert.deepEqual(expr(3, 4).toJS(), [4, 3]);
  });

  it ('loads a call', () => {
    const expr = evalCompiledForm(compileForm(`((fn (x y) [y x]) 3 4)`).compiledForm);
    assert.deepEqual(expr.toJS(), [4, 3]);
  });

  it ('loads a defined function', () => {
    const def = compileForm(`(def (flip x y) [y x])`, new NSEnv());
    const expr = compileForm(`(flip 3 4)`, def.nsEnv);
    const result = evalCompiledForm(`(function () {${def.compiledForm} \n return ${expr.compiledForm};})()`);
    assert.deepEqual(result.toJS(), [4, 3]);
  });

  it ('execs a JS global function', () => {
    const expr = evalCompiledForm(compileForm(`(js/process.cwd)`).compiledForm);
    assert.equal(expr, process.cwd());
  });

  it ('calls a fn referred in from another NS', () => {
    const result = compileForm(`(def flipped (flip 3 4))`, barNSEnv, fooEnv);
    const loadNS = evalNSCode(List.of(result.compiledForm), result.nsEnv, fooEnv);

    assert.deepEqual(loadNS(barNSEnv).exports.get('flipped').value.toJS(), [4, 3]);
  });

  it('calls a function in another namespace through its alias', () => {
    const result = compileForm(`(def flipped (foo/flip 3 4))`, barNSEnv, fooEnv);
    const loadNS = evalNSCode(List.of(result.compiledForm), result.nsEnv, fooEnv);

    assert.deepEqual(loadNS(barNSEnv).exports.get('flipped').value.toJS(), [4, 3]);
  });
});
