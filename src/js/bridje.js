#!/usr/bin/env node

const cli = require('commander');
const process = require('process');
const Runtime = require('./runtime');
const nsio = require('./nsio')
const {List} = require('immutable');

const cmd = new cli.Command();

cmd.command('build <entry-namespaces...>')
  .option('-t, --target-path <dir>', 'sets the target path for builds')
  .option('-p, --path <dir>[:<dir>...]', "sets the search path for Bridje namespaces - separated by ':'", path => List(path.split(':')))
  .action(function (entryNamespaces, options) {
    Runtime(nsio({projectPaths: options.path, targetPath: options.targetPath})).build(entryNamespaces);
  });

cmd.command('run <main-ns> [args...]')
  .option('-p, --path <dir>[:<dir>...]', "sets the search paths for Bridje namespaces - separated by ':'", path => List(path.split(':')))
  .option('-r, --repl [<host>:]port', 'starts a REPL server on the given interface/port')
  .action(function(mainNS, args, options) {
    Runtime(nsio({projectPaths: options.path})).runMain(mainNS, List(args));
  });

cmd.parse(process.argv);
