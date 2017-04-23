const path = require('path');
const fs = require('fs');
const mkdirp = require('mkdirp');

module.exports = function (projectPaths) {
  function nsToFilename(ns, ext) {
    return `${ns.replace(/\./g, '/')}.${ext}`;
  }

  function readFileAsync(filePath) {
    return new Promise((resolve, reject) => {
      fs.readFile(filePath, 'utf8', (err, res) => {
        if (err !== null) {
          reject(err);
        } else {
          resolve({brj: res, brjFile: filePath});
        }
      });
    });
  }

  function possiblePaths(filename) {
    const paths = projectPaths.map(projectPath => path.resolve(projectPath, filename));

    try {
      paths = paths.push(require.resolve(filename));
    } catch (e) {}

    return paths;
  }

  return {
    resolveNSAsync: function(ns) {
      let promise = Promise.reject('No project paths available.');

      const isFileError = err => err.syscall == 'open';
      const isFileNotExistsError = err => err.code == 'ENOENT';

      return possiblePaths(nsToFilename(ns, 'brj')).reduce(
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
    },

    resolveCachedNSAsync: function(ns) {
      // TODO
      return Promise.resolve(undefined);
    }
  };
};
