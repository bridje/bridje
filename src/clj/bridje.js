#!/usr/bin/env node

const cli = require('commander');
const process = require('process');
const {List, Map} = require('immutable');
const path = require('path');
const {readFile} = require('fs');

const cmd = new cli.Command();

const targetPath = path.resolve(process.cwd(), 'bridje-stuff', 'node');

cmd.command('run <main-ns> [args...]')
  .action(function(mainNS, args, options) {
    // TODO
    readFile(path.join(targetPath, mainNS.split(/\./).join('/') + '.js'), 'utf8', (err, data) => {
      console.log(err);
      console.log(data);
    });
  });

cmd.parse(process.argv);
