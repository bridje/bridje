const path = require('path');
const fs = require('fs');
const mkdirp = require('mkdirp');

module.exports = function({projectPaths, targetPath}) {
  function nsToFilename(ns, ext) {
    return `${ns.replace(/\./g, '/')}.${ext}`;
  }

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
          return readFileAsync(path.resolve(projectPaths[i], nsToFilename(ns, 'brj')));
        }
      });
    }

    return promise.catch(err => {
      if (isFileError(err) && isFileNotExistsError(err)) {
        return Promise.reject({error: 'ENOENT', projectPaths, ns});
      } else {
        return Promise.reject(err);
      }
    });
  }

  function resolveNSJSAsync(ns) {
    // TODO resolve the JS
    return Promise.reject();
  }

  function writeFileAsync(filePath, str) {
    return new Promise((resolve, reject) => {
      mkdirp(path.parse(filePath).dir, err => {
        if (err) {
          reject(err);
        } else {
          fs.writeFile(filePath, str, 'utf8', (err, res) => {
            if (err !== null) {
              reject(err);
            } else {
              resolve(res);
            }
          });
        }
      });
    });
  }

  function writeNSAsync(ns, str) {
    if (targetPath !== undefined) {
      return writeFileAsync(path.resolve(targetPath, nsToFilename(ns, 'js')), str);
    } else {
      return Promise.resolve();
    }
  }

  return {resolveNSAsync, resolveNSJSAsync, writeNSAsync};
};
