#!/usr/bin/env node

const cli = require('commander');
const process = require('process');
const {loadFormsAsync, compileForms, evalNodeForms} = require('./runtime');
const {readForms} = require('./reader');
const {Env} = require('./env');
const nsIO = require('./nsio');
const {List, Map} = require('immutable');
const brjVersion = require('./version');
const pathAPI = require('path');

const cmd = new cli.Command();

const targetPath = pathAPI.resolve(process.cwd(), 'bridje-stuff', brjVersion, 'node');

cmd.command('run <main-ns> [args...]')
  .option('-p, --path <dir>[:<dir>...]', "sets the search paths for Bridje namespaces - separated by ':'", path => List(path.split(':')))
  .option('-r, --repl [<host>:]port', 'starts a REPL server on the given interface/port')
  .action(function(mainNS, args, options) {
    return loadFormsAsync(undefined, mainNS, {
      nsIO: nsIO({projectPaths: options.path, targetPath}),
      readForms
    }).then(
      loadedNSs => {
        const env = loadedNSs.reduce(
          (env, loadedNS) =>
            evalNodeForms(env, compileForms(env, loadedNS)).env,
          new Env({}));

        const mainFn = env.getIn(['nsEnvs', mainNS, 'exports', 'main', 'value']);
        mainFn(List(args));
      });
  });

cmd.parse(process.argv);
