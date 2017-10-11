#!/usr/bin/env node

const cli = require('commander');
const process = require('process');
const {List, Map, Set} = require('immutable');
const path = require('path');
const {readFile} = require('fs');
const vm = require('vm');

function readFileAsync(filePath) {
  return new Promise(function (resolve, reject) {
    readFile(filePath, 'utf8', function (err, data) {
      if (err) {
        reject(err);
      } else {
        resolve(data);
      }
    });
  });
}

const targetPath = path.resolve(process.cwd(), 'bridje-stuff', 'node');

function readNSFileAsync(ns) {
  return readFileAsync(path.join(targetPath, ns.split(/\./).join('/') + '.js'))
}

function loadNSAsync(mainNS) {
  function loadNSAsync_ (toLoad, cbs, loaded) {
    if (toLoad.isEmpty()) {
      return cbs;
    } else {
      return Promise.all(toLoad.map(
        ns =>
          readNSFileAsync(ns).then(src => {
            return new vm.Script(`(function ({_bridje, require}) {${src}})`).runInThisContext()({require})}))).then(
              results =>
                loadNSAsync_(Set(List(results).flatMap(r => r.deps)).subtract(loaded),
                             List(results).map(r => r.cb).concat(cbs),
                             loaded.union(toLoad)));
    }
  }

  return loadNSAsync_(Set.of(mainNS), List(), Set())
    .then(cbs => cbs.reduce((env, cb) => cb(env), Map()));
}


const cmd = new cli.Command();

cmd.command('run <main-ns> [args...]')
  .action(function(mainNS, args, options) {
    loadNSAsync(mainNS).then(env => {
      const mainFn = env.getIn([mainNS, 'vars', 'main']);

      if (mainFn) {
        console.log(mainFn(List(args)));
      }
    });
  });

cmd.parse(process.argv);
