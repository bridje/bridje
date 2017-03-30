const {Record, Map} = require('immutable');
const {Env} = require('./runtime');

function envManager() {
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

module.exports = {envManager};
