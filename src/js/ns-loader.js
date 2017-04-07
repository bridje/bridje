const path = require('path');
const fs = require('fs');

module.exports = function(projectPaths) {
  function readFileAsync(path) {
    return new Promise((resolve, reject) => {
      fs.readFile(path, 'utf8', (err, res) => {
        if (err !== null) {
          reject(err);
        } else {
          resolve(res);
        }
      });
    });
  }

  /// returns Promise<String>
  function resolveNSAsync(ns) {
    var promise = Promise.reject('No project paths available.');

    var isFileError = err => err.syscall == 'open';
    var isFileNotExistsError = err => err.code == 'ENOENT';

    for (let i = 0; i < projectPaths.length; i++) {
      promise = promise.catch((err) => {
        if (isFileError(err) && !isFileNotExistsError(err)) {
          return Promise.reject(err);
        } else {
          return readFileAsync(path.resolve(process.cwd(), projectPaths[i], ns.replace(/\./g, '/') + '.brj'));
        }
      });
    }

    return promise.catch(err => {
      if (isFileError(err) && isFileNotExistsError(err)) {
        return Promise.reject({
          error: 'ENOENT',
          projectPaths: projectPaths,
          ns: ns
        });
      } else {
        return Promise.reject(err);
      }
    });
  }

  return {resolveNSAsync};
};
