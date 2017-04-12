#!/usr/bin/env node

const cli = require('commander')
const process = require('process');

const cmd = new cli.Command();

cmd.command('build <entry-namespaces...>')
  .option('-t, --target-path <dir>', 'sets the target path for builds')
  .option('-p, --path <dir>[:<dir>...]', 'sets the search path for Bridje namespaces')
  .action(function (entryNamespaces, options) {
    console.log('yo!', entryNamespaces, options);
  });

cmd.command('run <main-ns> [args...]')
  .option('-p, --path <dir>[:<dir>...]', 'sets the search path for Bridje namespaces')
  .option('-r, --repl [<host>:]port', 'starts a REPL server on the given interface/port')
  .action(function(mainNS, args, options) {
    console.log('sup!', mainNS, args, options);
  });

cmd.parse(process.argv);
