var assert = require('assert');
var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');
var e = require('../../src/js/expr');
const {List, Map} = require('immutable');
const {Env, NSEnv, Var} = require('../../src/js/env');
const {fooEnv, fooNSEnv, flipVar, barNSEnv, JustVar, NothingVar} = require('./env.test');

describe('analyser', () => {
  it('reads an NS header', () => {
    const nsHeader = ana.readNSHeader('bridje.kernel.bar', '/bridje/kernel/bar.brj', reader.readForms(`
(ns bridje.kernel.bar
  {refers {bridje.kernel.foo [flip flop]}
   aliases {baz bridje.kernel.baz}})`).first());

    assert.deepEqual(nsHeader.toJS(), {
      ns: 'bridje.kernel.bar',
      brjFile: '/bridje/kernel/bar.brj',
      nsHash: null,
      refers: {
        flip: 'bridje.kernel.foo',
        flop: 'bridje.kernel.foo'
      },
      aliases: {
        baz: 'bridje.kernel.baz'
      }
    });
  });

  it('resolves an NS header', () => {
    const nsEnv = ana.resolveNSHeader(fooEnv, new ana.NSHeader({
      ns: 'bridje.kernel.bar',
      nsHash: 42,
      brjFile: '/bridje/kernel/bar.brj',
      refers: Map({
        flip: 'bridje.kernel.foo'
      }),
      aliases: Map({
        foo: 'bridje.kernel.foo'
      })
    }));

    assert.equal(nsEnv.refers.get('flip'), flipVar);
    assert.equal(nsEnv.aliases.get('foo'), fooNSEnv);
    assert.equal(nsEnv.nsHash, 42);
  });

  it('reads a simple value expr', () => {
    let expr = ana.analyseForm(null, null, reader.readForms('#{[3.4 "Hello!"] [42 false]}').first());

    assert.equal(expr.exprType, 'set');
    const outerSetExprs = expr.exprs;
    assert.equal(expr.exprs.size, 2);

    assert.equal(outerSetExprs.get(0).exprType, 'vector');

    const vec0 = outerSetExprs.get(0);
    const vec0Exprs = vec0.exprs;
    assert.equal(vec0.exprType, 'vector');
    assert.equal(vec0Exprs.get(0).float, 3.4);
    assert.equal(vec0Exprs.get(1).str, 'Hello!');

    const vec1 = outerSetExprs.get(1);
    const vec1Exprs = vec1.exprs;
    assert.equal(vec1.exprType, 'vector');

    assert.equal(vec1Exprs.get(0).int, 42);
    assert.strictEqual(vec1Exprs.get(1).bool, false);
  });

  it ('analyses a record', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('{a 1, b "Hello"}').first());

    assert.equal(expr.exprType, 'record');
    assert.equal(expr.entries.size, 2);

    const entry0 = expr.entries.get(0);
    assert.equal(entry0.key, 'a');
    assert.equal(entry0.value.int, 1);

    const entry1 = expr.entries.get(1);
    assert.equal(entry1.key, 'b');
    assert.equal(entry1.value.str, 'Hello');
  });

  it ('analyses an if-expr', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('(if false 24 42)').first());

    assert.equal(expr.exprType, 'if');

    const testExpr = expr.testExpr;
    assert.equal(testExpr.exprType, 'bool');
    assert.strictEqual(testExpr.bool, false);

    const thenExpr = expr.thenExpr;
    assert.equal(thenExpr.exprType, 'int');
    assert.equal(thenExpr.int, 24);

    const elseExpr = expr.elseExpr;
    assert.equal(elseExpr.exprType, 'int');
    assert.equal(elseExpr.int, 42);
  });

  it ('analyses a let-expr', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('(let [x 4, y 5] [x y])').first());

    assert.equal(expr.exprType, 'let');
    assert.equal(expr.bindings.size, 2);

    const binding0 = expr.bindings.get(0);
    assert.equal(binding0.localVar.name, 'x');
    const lv0 = binding0.localVar;
    assert.strictEqual(binding0.expr.int, 4);

    const binding1 = expr.bindings.get(1);
    assert.equal(binding1.localVar.name, 'y');
    const lv1 = binding1.localVar;
    assert.strictEqual(binding1.expr.int, 5);

    const body = expr.body;
    assert.equal(body.exprType, 'vector');
    const bodyVecExprs = body.exprs;

    assert.equal(bodyVecExprs.get(0).localVar, lv0);
    assert.equal(bodyVecExprs.get(1).localVar, lv1);
  });

  it ('analyses a def constant', () => {
    const expr = ana.analyseForm(null, new NSEnv(), reader.readForms('(def hello "hello world!")').first());

    assert.equal(expr.exprType, 'def');
    assert.equal(expr.sym.name, 'hello');
    assert.equal(expr.body.str, 'hello world!');
  });

  it ('analyses a def function', () => {
    const expr = ana.analyseForm(null, new NSEnv(), reader.readForms('(def (flip x y) [y x])').first());
    assert.equal(expr.exprType, 'def');
    assert.equal(expr.sym.name, 'flip');
    assert.equal(expr.params.size, 2);

    assert.equal(expr.body.exprType, 'vector');
    const bodyExprs = expr.body.exprs;
    assert.equal(bodyExprs.get(0).localVar, expr.params.get(1));
    assert.equal(bodyExprs.get(1).localVar, expr.params.get(0));
  });

  it ('analyses a defmacro', () => {
    const expr = ana.analyseForm(null, new NSEnv(), reader.readForms(`(defmacro (simple-macro a b) '["hello" "world"])`).first());
    assert.equal(expr.exprType, 'defmacro');
    assert.equal(expr.sym.name, 'simple-macro');
    assert.equal(expr.params.size, 2);

    assert.equal(expr.body.exprType, 'quoted');
    assert.equal(expr.body.form.formType, 'vector');
    const bodyForms = expr.body.form.forms;
    assert.equal(bodyForms.get(0).str, 'hello');
    assert.equal(bodyForms.get(1).str, 'world');
  });

  it ('analyses a var within the namespace', () => {
    const nsEnvVar = new Var({});
    const nsEnv = new NSEnv({}).setIn(['exports', 'foo'], nsEnvVar);
    const expr = ana.analyseForm(null, nsEnv, reader.readForms('foo').first());

    assert.equal(expr.exprType, 'var');
    assert.equal(expr.var, nsEnvVar);
  });

  it ('analyses a fnexpr', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('(fn (x y) [y x])').first());
    assert.equal(expr.exprType, 'fn');
    assert.equal(expr.params.size, 2);

    assert.equal(expr.body.exprType, 'vector');
    const bodyExprs = expr.body.exprs;
    assert.equal(bodyExprs.get(0).localVar, expr.params.get(1));
    assert.equal(bodyExprs.get(1).localVar, expr.params.get(0));
  });

  it ('analyses a fn call', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('((fn (x y) [y x]) 3 4)').first());
    assert.equal(expr.exprType, 'call');
    assert.equal(expr.exprs.get(0).exprType, 'fn');
    assert.equal(expr.exprs.get(1).int, 3);
    assert.equal(expr.exprs.get(2).int, 4);
  });

  it ('analyses a JS global call', () => {
    const expr = ana.analyseForm(null, null, reader.readForms(`(js/process.cwd)`).first());
    assert.equal(expr.exprType, 'call');
    assert.equal(expr.exprs.size, 1);

    const jsGlobal = expr.exprs.first();
    assert.equal(jsGlobal.exprType, 'jsGlobal');
    assert.deepEqual(jsGlobal.path.toJS(), ['process', 'cwd']);
  });

  it ('refers an export in another file', () => {
    assert.deepEqual(barNSEnv.refers.get('flip'), flipVar);
  });

  it ('resolves an export in another file', () => {
    const expr = ana.analyseForm(fooEnv, barNSEnv, reader.readForms('flip').first());
    assert.equal(expr.exprType, 'var');
    assert.equal(expr.var, flipVar);
  });

  it('resolves a function in another namespace through its alias', () => {
    const expr = ana.analyseForm(fooEnv, barNSEnv, reader.readForms('foo/flip').first());
    assert.equal(expr.exprType, 'var');
    assert.equal(expr.var, flipVar);
    assert.equal(expr.alias, 'foo');
  });

  it('analyses a vector-style defdata form', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('(defdata (Just a))').first());
    assert.equal(expr.exprType, 'defdata');
    assert.equal(expr.name, 'Just');
    assert.equal(expr.type, 'vector');
    assert.deepEqual(expr.params.toJS(), ['a']);
  });

  it('analyses a value-style defdata form', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('(defdata Nothing)').first());
    assert.equal(expr.exprType, 'defdata');
    assert.equal(expr.name, 'Nothing');
    assert.equal(expr.type, 'value');
  });

  it('analyses a record-style defdata form', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('(defdata (Person #{name, address, phone-number}))').first());
    assert.equal(expr.exprType, 'defdata');
    assert.equal(expr.name, 'Person');
    assert.equal(expr.type, 'record');
    assert.deepEqual(expr.keys.toJS(), ['name', 'address', 'phone-number']);
  });

  it('analyses a match', () => {
    const expr = ana.analyseForm(fooEnv, fooNSEnv, reader.readForms('(match (Just 4) Nothing "oh no", Just "aww yiss")').first());
    assert.equal(expr.exprType, 'match');
    assert.equal(expr.clauses.size, 2);

    const c0 = expr.clauses.get(0);
    assert.equal(c0.var, NothingVar);
    assert.equal(c0.expr.str, 'oh no');

    const c1 = expr.clauses.get(1);
    assert.equal(c1.var, JustVar);
    assert.equal(c1.expr.str, 'aww yiss');
  });

  it('analyses a match in another ns', () => {
    const expr = ana.analyseForm(fooEnv, barNSEnv, reader.readForms('(match (foo/Just 4) foo/Nothing "oh no", Just "aww yiss")').first());
    assert.equal(expr.exprType, 'match');
    assert.equal(expr.clauses.size, 2);

    const c0 = expr.clauses.get(0);
    assert.equal(c0.var, NothingVar);
    assert.equal(c0.expr.str, 'oh no');

    const c1 = expr.clauses.get(1);
    assert.equal(c1.var, JustVar);
    assert.equal(c1.expr.str, 'aww yiss');
  });

  it('analyses a loop-recur', async () => {
    const expr = ana.analyseForm(fooEnv, barNSEnv, reader.readForms('(loop [x 5, res []] (if (foo/pos? x) (recur (foo/dec x) (foo/push res x)) res))').first());
    assert.equal(expr.exprType, 'loop');
    assert.equal(expr.bindings.size, 2);

    const binding0 = expr.bindings.get(0);
    assert.equal(binding0.localVar.name, 'x');
    const lv0 = binding0.localVar;
    assert.strictEqual(binding0.expr.int, 5);

    const binding1 = expr.bindings.get(1);
    assert.equal(binding1.localVar.name, 'res');
    const lv1 = binding1.localVar;
    assert(binding1.expr.exprs.isEmpty());

    const body = expr.body;
    assert.equal(body.exprType, 'if');
    assert.strictEqual(body.elseExpr.localVar, lv1);

    const thenExpr = body.thenExpr;
    assert.equal(thenExpr.exprType, 'recur');
    assert.equal(thenExpr.bindings.size, 2);

    const recur0 = thenExpr.bindings.get(0);
    assert.strictEqual(recur0.localVar, lv0);
    assert.equal(recur0.expr.exprType, 'call');
    assert.equal(recur0.expr.exprs.get(1).localVar, lv0);

    const recur1 = thenExpr.bindings.get(1);
    assert.strictEqual(recur1.localVar, lv1);
    assert.equal(recur1.expr.exprType, 'call');
    assert.equal(recur1.expr.exprs.get(1).localVar, lv1);
    assert.equal(recur1.expr.exprs.get(2).localVar, lv0);
  });

  it('analyses a recursive call', () => {
    const expr = ana.analyseForm(fooEnv, barNSEnv, reader.readForms('(def (foo x) (foo x))').first());
    assert.equal(expr.body.exprType, 'call');
    const callVar = expr.body.exprs.first().var;
    assert.equal(callVar.ns, barNSEnv.ns);
    assert.equal(callVar.name, 'foo');
  });
});
