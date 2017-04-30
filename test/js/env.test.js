var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');
const {Record, List, Map} = require('immutable');
const {Env, NSEnv, Var, DataType} = require('../../src/js/env');

const flipVar = Var({
  name: 'flip',
  ns: 'bridje.kernel.foo',
  safeName: 'flip',
  value: (x, y) => List.of(y, x)
});

const JustDataType = new DataType({ns: 'bridje.kernel.foo', name: 'Just'});
const JustRecord = Record({_params: ['a']});
JustRecord.prototype._brjType = JustDataType;
const JustVar = new Var({
  name: 'Just',
  ns: 'bridje.kernel.foo',
  safeName: 'Just',
  value: (a) => new JustRecord({_params: List.of(a)})
});

const NothingDataType = new DataType({ns: 'bridje.kernel.foo', name: 'Nothing'});
const NothingRecord = Record({});
NothingRecord.prototype._brjType = NothingDataType;
const NothingVar = new Var({
  name: 'Nothing',
  ns: 'bridje.kernel.foo',
  safeName: 'Nothing',
  value: new NothingRecord({})
});

const fooNSEnv = new NSEnv({
  ns: 'bridje.kernel.foo',
  brjFile: '/bridje/kernel/foo.brj',
  exports: Map({
    'flip': flipVar,
    Just: JustVar,
    Nothing: NothingVar
  }),
  dataTypes: Map({
    Just: JustDataType,
    Nothing: NothingDataType
  })
});

const fooEnv = new Env({
  nsEnvs: new Map({
    'bridje.kernel.foo': fooNSEnv
  })});

const barNSDecl = '(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip]}, aliases {foo bridje.kernel.foo}})';
const barNSEnv = ana.resolveNSHeader(fooEnv, ana.readNSHeader('bridje.kernel.bar', '/bridje/kernel/bar.brj', reader.readForms(barNSDecl).first()));

module.exports = {flipVar, fooEnv, fooNSEnv, barNSDecl, barNSEnv, JustDataType, NothingDataType};
