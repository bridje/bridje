var assert = require('assert');
var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');
var e = require('../../src/js/expr');
const {List, Map} = require('immutable');
const {Env, NSEnv, Var} = require('../../src/js/env');
const {fooEnv, fooNSEnv, flipVar, barNSDecl, barNSEnv} = require('./env.test');

describe('analyser', () => {
  it('reads an NS header', () => {
    const nsHeader = ana.readNSHeader('bridje.kernel.bar', reader.readForms(`
(ns bridje.kernel.bar
  {refers {bridje.kernel.foo [flip flop]}
   aliases {baz bridje.kernel.baz}})`).first());

    assert.deepEqual(nsHeader.toJS(), {
      ns: 'bridje.kernel.bar',
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
      refers: Map({
        flip: 'bridje.kernel.foo'
      }),
      aliases: Map({
        foo: 'bridje.kernel.foo'
      })
    }));

    assert.equal(nsEnv.refers.get('flip'), flipVar);
    assert.equal(nsEnv.aliases.get('foo'), fooNSEnv);
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
    assert.equal(entry0.key.name, 'a');
    assert.equal(entry0.value.int, 1);

    const entry1 = expr.entries.get(1);
    assert.equal(entry1.key.name, 'b');
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
    const expr = ana.analyseForm(null, null, reader.readForms('(def hello "hello world!")').first());

    assert.equal(expr.exprType, 'def');
    assert.equal(expr.sym.name, 'hello');
    assert.equal(expr.body.str, 'hello world!');
  });

  it ('analyses a def function', () => {
    const expr = ana.analyseForm(null, null, reader.readForms('(def (flip x y) [y x])').first());
    assert.equal(expr.exprType, 'def');
    assert.equal(expr.sym.name, 'flip');
    assert.equal(expr.params.size, 2);

    assert.equal(expr.body.exprType, 'vector');
    const bodyExprs = expr.body.exprs;
    assert.equal(bodyExprs.get(0).localVar, expr.params.get(1));
    assert.equal(bodyExprs.get(1).localVar, expr.params.get(0));
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
});
