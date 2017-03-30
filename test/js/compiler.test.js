const {compileExpr, compileNS} = require('../../src/js/compiler');
const {readForms} = require('../../src/js/reader');
const {analyseForm} = require('../../src/js/analyser');
const runtime = require('../../src/js/runtime');
const im = require('immutable');
const {Record} = im;
const vm = require('vm');

describe('compiler', () => {
  it('compiles a NS', () => {
    let expr = analyseForm(null, null, readForms('(def foo-bar ["hello world!"])').first());
    let compileResult = compileExpr(null, new runtime.NSEnv({}), expr);
    let compiledNS = compileNS(null, compileResult.nsEnv, compileResult.code);
    console.log(new vm.Script(compiledNS).runInThisContext()(runtime, im).f());
  });

});
