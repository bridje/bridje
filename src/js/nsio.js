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

  function possiblePaths(filename) {
    const paths = projectPaths.map(projectPath => path.resolve(projectPath, filename));
    if (targetPath) {
      paths = paths.push(path.resolve(targetPath, filename));
    }

    try {
      paths = paths.push(require.resolve(filename));
    } catch (e) {}

    return paths;
  }

  function resolveNSAsync(ns, ext) {
    let promise = Promise.reject('No project paths available.');

    const isFileError = err => err.syscall == 'open';
    const isFileNotExistsError = err => err.code == 'ENOENT';

    return possiblePaths(nsToFilename(ns, ext)).reduce(
      (promise, possiblePath) => promise.catch((err) => {
        if (isFileError(err) && !isFileNotExistsError(err)) {
          return Promise.reject(err);
        } else {
          return readFileAsync(possiblePath);
        }
      }), promise)

      .catch(
        err => {
          if (isFileError(err) && isFileNotExistsError(err)) {
            return Promise.reject({error: 'ENOENT', projectPaths, ns});
          } else {
            return Promise.reject(err);
          }
        });
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

  return {resolveNSAsync, writeNSAsync};
};
