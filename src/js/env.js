var im = require('immutable');
var Record = im.Record;
var Map = im.Map;

var Env = Record({nsEnvs: im.Map({})});

module.exports = function() {
  var env = new Env({});

  var envQueue = [];
  var running = false;

  async function runQueue() {
    var f = envQueue.shift();
    await f();

    if (envQueue.length > 0) {
      setTimeout(runQueue, 0);
    } else {
      running = false;
    }
  }

  return {
    currentEnv: function() {
      return env;
    },

    updateEnv: function(f) {
      return new Promise(function (resolve, reject) {
        envQueue.push(async function() {
          env = await f(env);
          resolve(env);
        });

        if (!running) {
          running = true;
          runQueue();
        }
      });
    }
  };
};

module.exports.Env = Env;
