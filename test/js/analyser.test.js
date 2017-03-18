var assert = require('assert');
var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');
var e = require('../../src/js/expr');

describe('analyser', () => {
  // it('reads an NS form', () => {
  //   console.log(ana.analyseNSForm(null, 'bridje.kernel', reader.readForms(`(nsa bridje.kernel)`).first()));
  // });

  it('reads a simple value expr', () => {
    let expr = ana.analyseForm(null, null, reader.readForms('#{[3.4 "Hello!"] [42 false]}').first());
    assert(expr instanceof e.Expr);

    const outerSet = expr.expr;
    assert.equal(outerSet.exprType, 'set');
    const outerSetExprs = outerSet.exprs;
    assert.equal(outerSet.exprs.size, 2);

    assert.equal(outerSetExprs.get(0).expr.exprType, 'vector');

    const vec0 = outerSetExprs.get(0).expr;
    const vec0Exprs = vec0.exprs;
    assert.equal(vec0.exprType, 'vector');
    assert.equal(vec0Exprs.get(0).expr.float, 3.4);
    assert.equal(vec0Exprs.get(1).expr.str, 'Hello!');

    const vec1 = outerSetExprs.get(1).expr;
    const vec1Exprs = vec1.exprs;
    assert.equal(vec1.exprType, 'vector');

    assert.equal(vec1Exprs.get(0).expr.int, 42);
    assert.strictEqual(vec1Exprs.get(1).expr.bool, false);
  });
});
