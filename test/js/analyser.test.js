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
});
