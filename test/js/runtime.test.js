var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');
const {List, Map} = require('immutable');
const {Env, NSEnv, Var} = require('../../src/js/runtime');

const flipVar = Var({
  name: 'flip',
  ns: 'bridje.kernel.foo',
  safeName: 'flip',
  value: (x, y) => List.of(y, x)
});

const fooNSEnv = new NSEnv({
  ns: 'bridje.kernel.foo',
  exports: Map({
    'flip': flipVar
  })
});

const fooEnv = new Env({
  nsEnvs: new Map({
    'bridje.kernel.foo': fooNSEnv
  })});

const barNSDecl = '(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip]}})';
const barNSEnv = ana.analyseNSForm(fooEnv, 'bridje.kernel.bar', reader.readForms(barNSDecl).first());

module.exports = {flipVar, fooEnv, fooNSEnv, barNSDecl, barNSEnv};
