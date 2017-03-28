const {compileExpr, compileNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const im = require('immutable');
const {Record} = im;
const vm = require('vm');

describe('compiler', () => {
  it('compiles a NS', () => {
    let brjNS = Record({imports: null, f: null});
    let expr = analyseForm(null, null, readForms('(def foo ["hello world!"])').first());
    let compiledNS = new vm.Script(compileNS(null, null, compileExpr(null, null, expr))).runInThisContext()(brjNS, im);
    console.log(compiledNS);
  });

});
