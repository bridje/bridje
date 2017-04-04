#!/usr/bin/env node

const ev = require('./eval')(['src/brj', 'src/js/test', 'test/brj']);
const {List} = require('immutable');
const argv = List(process.argv).slice(2);

ev.runMain(argv.first(), argv.shift());
