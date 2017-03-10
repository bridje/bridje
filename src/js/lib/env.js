var im = require('immutable');
var Record = im.Record;

var Env = Record({nsEnvs: {}});

var env = new Env({});

var envQueue = [];
var running = false;

function currentEnv() {
  return env;
}

function runQueue() {
  var f = envQueue.shift();
  f();
  if (envQueue.length > 0) {
    setTimeout(runQueue, 0);
  } else {
    running = false;
  }
}

function updateEnv(f) {
  return new Promise(function (resolve, reject) {
    envQueue.push(function() {
      // TODO
    });

    if (!running) {
      runQueue();
    }

  });
}

module.exports = {
  Env: Env,
  currentEnv: currentEnv,
  updateEnv: updateEnv
};
