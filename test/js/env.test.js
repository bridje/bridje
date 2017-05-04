var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');
const {Record, List, Map} = require('immutable');
const {Env, NSEnv, Var, makeSafe} = require('../../src/js/env');

function fooVar(name, value) {
  return new Var({ns: 'bridje.kernel.foo', name, safeName: makeSafe(name), value});
}

const flipVar = fooVar('flip', (x, y) => List.of(y, x));

const JustRecord = Record({_params: ['a']});
const JustVar = fooVar('Just', (a) => new JustRecord({_params: List.of(a)}));

JustRecord.prototype._brjType = JustVar.value;

const NothingRecord = Record({});
const NothingVar = fooVar('Nothing', new NothingRecord({}));
NothingRecord.prototype._brjType = NothingVar.value;

const fooNSEnv = new NSEnv({
  ns: 'bridje.kernel.foo',
  brjFile: '/bridje/kernel/foo.brj',
  exports: Map({
    'flip': flipVar,
    'Just': JustVar,
    'Nothing': NothingVar,
    'pos?': fooVar('pos?', x => x > 0),
    'dec': fooVar('dec', x => x - 1),
    'push': fooVar('push', (coll, el) => coll.push(el))
  })
});

const fooEnv = new Env({
  nsEnvs: new Map({
    'bridje.kernel.foo': fooNSEnv
  })});

const barNSDecl = '(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip Just]}, aliases {foo bridje.kernel.foo}})';
const barNSEnv = ana.resolveNSHeader(fooEnv, ana.readNSHeader('bridje.kernel.bar', '/bridje/kernel/bar.brj', reader.readForms(barNSDecl).first()));

module.exports = {flipVar, fooEnv, fooNSEnv, barNSDecl, barNSEnv, JustVar, NothingVar};
