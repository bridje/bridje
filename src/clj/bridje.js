#!/usr/bin/env node

const cli = require('commander');
const process = require('process');
const {List, Map} = require('immutable');
const brjVersion = require('./version');
const pathAPI = require('path');

const cmd = new cli.Command();

const targetPath = pathAPI.resolve(process.cwd(), 'bridje-stuff', brjVersion, 'node');

cmd.command('run <main-ns> [args...]')
  .action(function(mainNS, args, options) {
    // TODO
    console.log('Hello!', mainNS, args)
  });

cmd.parse(process.argv);
