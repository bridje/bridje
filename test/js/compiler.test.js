const {compileExpr, compileNodeNS, compileWebNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const e = require('../../src/js/env');
const {NSEnv, Env} = e;
const im = require('immutable');
const {List, Map, Record} = im;
const vm = require('vm');
const assert = require('assert');
const {fooEnv, flipEnv, flipVar, JustVar, NothingVar, barNSDecl, barNSEnv} = require('./env.test');
const {wrapWebJS} = require('./webpack.test.js');

function evalCompiledForm(compiledForm) {
  return new vm.Script(`(function (_env, _im) {return ${compiledForm};})`).runInThisContext()(e, im);
}

function evalCompiledForms(compiledForms) {
  return evalCompiledForm(`(function() {let _exports = _im.Map({}); ${compiledForms.join(' ')}})()`);
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
      const result = evalCompiledForms(List.of(r0.compiledForm, r1.compiledForm, `return double;`));

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
      const result = evalCompiledForms(List.of(def.compiledForm, `return ${expr.compiledForm}`));
      assert.deepEqual(result.toJS(), [4, 3]);
    });

    it ('execs a JS global function', () => {
      const expr = evalCompiledForm(compileForm(`(js/process.cwd)`).compiledForm);
      assert.equal(expr, process.cwd());
    });

    it ('compiles a vector-style defdata', () => {
      const def = compileForm(`(defdata (Just a))`, new NSEnv());
      const expr = compileForm(`(Just 3)`, def.nsEnv);
      const result = evalCompiledForms(List.of(def.compiledForm, `return ${expr.compiledForm}`));
      assert.deepEqual(result.toJS(), {_params: [3]});
    });

    it ('compiles a value-style defdata', () => {
      const def = compileForm(`(defdata Nothing)`, new NSEnv());

      const expr = compileForm(`Nothing`, def.nsEnv);
      const result = evalCompiledForms(List.of(def.compiledForm, `return ${expr.compiledForm}`));
    });

    it ('compiles a record-style defdata', () => {
      const def = compileForm(`(defdata (Person #{name, address, phone-number}))`, new NSEnv());

      const expr = compileForm(`(Person {name "James", address "Foo", phone-number "01234 567890"})`, def.nsEnv);
      const result = evalCompiledForms(List.of(def.compiledForm, `return ${expr.compiledForm}`));

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
      const result = evalCompiledForms(List.of(defJust.compiledForm, defNothing.compiledForm, `return ${expr.compiledForm}`));
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
      const webNS = evalWebNSCode(List.of(result.compiledForm), result.nsEnv, fooEnv, Map({'/bridje/kernel/foo.brj': Map({'flip': flipVar, Just: JustVar, Nothing: NothingVar})}));

      assert.deepEqual(webNS.get('flipped').value.toJS(), [4, 3]);
    });

    it('imports a function in another namespace through its alias', () => {
      const result = compileForm(`(def flipped {flipped (foo/flip 3 4)})`, barNSEnv, fooEnv);
      const webNS = evalWebNSCode(List.of(result.compiledForm), result.nsEnv, fooEnv, Map({'/bridje/kernel/foo.brj': Map({'flip': flipVar, Just: JustVar, Nothing: NothingVar})}));

      assert.deepEqual(webNS.get('flipped').value.toJS(), {flipped: [4, 3]});
    });
  });
});
