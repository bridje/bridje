var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');
const {Record, List, Map} = require('immutable');
const {Env, NSEnv, Var} = require('../../src/js/env');

const flipVar = Var({
  name: 'flip',
  ns: 'bridje.kernel.foo',
  safeName: 'flip',
  value: (x, y) => List.of(y, x)
});

const JustRecord = Record({_params: ['a']});
const JustVar = new Var({
  name: 'Just',
  ns: 'bridje.kernel.foo',
  safeName: 'Just',
  value: (a) => new JustRecord({_params: List.of(a)})
});

JustRecord.prototype._brjType = JustVar.value;

const NothingRecord = Record({});
const NothingVar = new Var({
  name: 'Nothing',
  ns: 'bridje.kernel.foo',
  safeName: 'Nothing',
  value: new NothingRecord({})
});
NothingRecord.prototype._brjType = NothingVar.value;

const fooNSEnv = new NSEnv({
  ns: 'bridje.kernel.foo',
  brjFile: '/bridje/kernel/foo.brj',
  exports: Map({
    'flip': flipVar,
    'Just': JustVar,
    'Nothing': NothingVar
  })
});

const fooEnv = new Env({
  nsEnvs: new Map({
    'bridje.kernel.foo': fooNSEnv
  })});

const barNSDecl = '(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip Just]}, aliases {foo bridje.kernel.foo}})';
const barNSEnv = ana.resolveNSHeader(fooEnv, ana.readNSHeader('bridje.kernel.bar', '/bridje/kernel/bar.brj', reader.readForms(barNSDecl).first()));

module.exports = {flipVar, fooEnv, fooNSEnv, barNSDecl, barNSEnv, JustVar, NothingVar};
