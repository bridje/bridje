const {compileExpr, compileNodeNS, compileWebNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const e = require('../../src/js/env');
const {NSEnv, Env, DataType} = e;
const im = require('immutable');
const {List, Map, Record} = im;
const vm = require('vm');
const assert = require('assert');
const {fooEnv, flipEnv, flipVar, barNSDecl, barNSEnv} = require('./env.test');
const {wrapWebJS} = require('./webpack.test.js');

function evalCompiledForm(compiledForm) {
  return new vm.Script(`(function (_env, _im) {return ${compiledForm};})`).runInThisContext()(e, im);
}

function evalNSCode(compiledForms, nsEnv) {
  return new vm.Script(compileNodeNS(nsEnv, compiledForms)).runInThisContext()(e, im);
}

function evalWebNSCode(compiledForms, nsEnv, env, requires) {
  return wrapWebJS(compileWebNS(env, nsEnv, compiledForms), requires);
}

function compileForm(form, nsEnv, env) {
  return compileExpr(env, nsEnv, analyseForm(env, nsEnv, readForms(form).first()));
}

describe('compiler', () => {
  describe('forms', () => {
    it('loads a vector', () => {
      const result = evalCompiledForm(compileForm('["hello world!"]').compiledForm);
      assert.deepEqual(result.toJS(), ['hello world!']);
    });

    it('loads a record', () => {
      const result = evalCompiledForm(compileForm('{a 1, b 2}').compiledForm);
      assert.deepEqual(result.toJS(), {a: 1, b: 2});
    });

    it ('loads a let', () => {
      const result = evalCompiledForm(compileForm(`(let [x 1, y 2] [x y])`).compiledForm);
      assert.deepEqual(result.toJS(), [1, 2]);
    });

    it ('loads a double hello', () => {
      const r0 = compileForm(`(def hello "hello")`, new NSEnv());
      const r1 = compileForm(`(def double [hello hello])`, r0.nsEnv);
      const result = evalCompiledForm(`(function () {let _exports = _im.Map({}); ${r0.compiledForm}\n${r1.compiledForm} \n return double;})()`);
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
      const result = evalCompiledForm(`(function () {let _exports = _im.Map({}); ${def.compiledForm} \n return ${expr.compiledForm};})()`);
      assert.deepEqual(result.toJS(), [4, 3]);
    });

    it ('execs a JS global function', () => {
      const expr = evalCompiledForm(compileForm(`(js/process.cwd)`).compiledForm);
      assert.equal(expr, process.cwd());
    });

    it ('compiles a vector-style defdata', () => {
      const def = compileForm(`(defdata (Just a))`, new NSEnv());
      const resultDataType = def.nsEnv.dataTypes.get('Just');
      assert(resultDataType instanceof DataType);
      assert.equal(resultDataType.name, 'Just');

      const justExpr = compileForm(`(Just 3)`, def.nsEnv);
      const justResult = evalCompiledForm(`(function () {let _dataTypes = _im.Map(); ${def.compiledForm} \n return ${justExpr.compiledForm};})()`);
      assert(justResult._brjType instanceof DataType);
      assert.equal(justResult._brjType.name, 'Just');
      assert.deepEqual(justResult.toJS(), {_params: [3]});
    });

    it ('compiles a value-style defdata', () => {
      const def = compileForm(`(defdata Nothing)`, new NSEnv());
      const resultDataType = def.nsEnv.dataTypes.get('Nothing');
      assert(resultDataType instanceof DataType);
      assert.equal(resultDataType.name, 'Nothing');

      const nothingExpr = compileForm(`Nothing`, def.nsEnv);
      const nothingResult = evalCompiledForm(`(function () {let _dataTypes = _im.Map(); ${def.compiledForm} \n return ${nothingExpr.compiledForm};})()`);
      assert(nothingResult._brjType instanceof DataType);
      assert.equal(nothingResult._brjType.name, 'Nothing');
    });

    it ('compiles a record-style defdata', () => {
      const def = compileForm(`(defdata (Person #{name, address, phone-number}))`, new NSEnv());
      const resultDataType = def.nsEnv.dataTypes.get('Person');
      assert(resultDataType instanceof DataType);
      assert.equal(resultDataType.name, 'Person');

      const expr = compileForm(`(Person {name "James", address "Foo", phone-number "01234 567890"})`, def.nsEnv);
      const result = evalCompiledForm(`(function () {let _dataTypes = _im.Map(); ${def.compiledForm} \n return ${expr.compiledForm};})()`);

      assert(result._brjType instanceof DataType);
      assert.equal(result._brjType.name, 'Person');
      assert.deepEqual(result.toJS(), {
        name: "James",
        address: "Foo",
        'phone-number': "01234 567890"
      });
    });

    it ('switches based on a datatype', () => {
      const defJust = compileForm(`(defdata (Just a))`, new NSEnv());
      const defNothing = compileForm(`(defdata Nothing)`, defJust.nsEnv);

      const expr = compileForm(`(match (Just 3) Nothing "oh no" Just "aww yiss") `, defNothing.nsEnv);
      const result = evalCompiledForm(`(function () {let _dataTypes = new _im.Map({}).asMutable(); ${defJust.compiledForm} \n ${defNothing.compiledForm} \n return ${expr.compiledForm};})()`);
      assert.equal(result, 'aww yiss');
    });
  });

  describe('node namespaces', () => {
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

  describe ('web', () => {
    it('exports a webpack var', () => {
      const result = compileForm(`(def hello ["hello world"])`, new NSEnv());
      const webNS = evalWebNSCode(List.of(result.compiledForm), result.nsEnv, fooEnv, Map({}));

      assert.deepEqual(webNS.get('hello').value.toJS(), ['hello world']);
    });

    it ('imports a fn referred from another ns', () => {
      const result = compileForm(`(def flipped (flip 3 4))`, barNSEnv, fooEnv);
      const webNS = evalWebNSCode(List.of(result.compiledForm), result.nsEnv, fooEnv, Map({'/bridje/kernel/foo.brj': Map({'flip': flipVar})}));

      assert.deepEqual(webNS.get('flipped').value.toJS(), [4, 3]);
    });

    it('imports a function in another namespace through its alias', () => {
      const result = compileForm(`(def flipped {flipped (foo/flip 3 4)})`, barNSEnv, fooEnv);
      const webNS = evalWebNSCode(List.of(result.compiledForm), result.nsEnv, fooEnv, Map({'/bridje/kernel/foo.brj': Map({'flip': flipVar})}));

      assert.deepEqual(webNS.get('flipped').value.toJS(), {flipped: [4, 3]});
    });
  });
});
