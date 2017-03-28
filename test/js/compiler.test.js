const {compileExpr, compileNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const im = require('immutable');
const {Record} = im;
const vm = require('vm');
const {NSEnv} = require('../../src/js/env');

describe('compiler', () => {
  it('compiles a NS', () => {
    let brjNS = Record({imports: null, f: null});
    let expr = analyseForm(null, null, readForms('(def foo-bar ["hello world!"])').first());
    let compileResult = compileExpr(null, new NSEnv({}), expr);
    let compiledNS = compileNS(null, compileResult.nsEnv, compileResult.code);
    console.log(new vm.Script(compiledNS).runInThisContext()(brjNS, im).f());
  });

});
