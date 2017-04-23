const pathAPI = require('path');
const fs = require('fs');
const mkdirp = require('mkdirp');

module.exports = function ({projectPaths, targetPath}) {
  function nsToFilename(ns, ext) {
    return `${ns.replace(/\./g, '/')}.${ext}`;
  }

  function readFileAsync(filePath) {
    return new Promise((resolve, reject) => {
      fs.readFile(filePath, 'utf8', (err, res) => {
        if (err !== null) {
          reject(err);
        } else {
          resolve(res);
        }
      });
    });
  }

  function mkdirAsync(dirPath) {
    return new Promise((resolve, reject) => {
      mkdirp(dirPath, (err, res) => {
        if (err) {
          reject(err);
        } else {
          resolve(res);
        }
      });
    });
  }

  function writeFileAsync(filePath, content) {
    return new Promise((resolve, reject) => {
      fs.writeFile(filePath, content, 'utf8', (err, res) => {
        if (err !== null) {
          reject(err);
        } else {
          resolve(res);
        }
      });
    });
  }

  function possiblePaths(filename) {
    const paths = projectPaths.map(projectPath => pathAPI.resolve(projectPath, filename));

    try {
      paths = paths.push(require.resolve(filename));
    } catch (e) {}

    return paths;
  }

  function makePathSafe(s) {
    return s.replace(/\./g, '_');
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
            return readFileAsync(possiblePath).then(brj => ({brj, brjFile: possiblePath}));
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
      const pathSafeNS = makePathSafe(ns);
      return readFileAsync(pathAPI.resolve(targetPath, `${pathSafeNS}.header.json`))
        .then(headerFile => {
          const nsCodeAsync = readFileAsync(pathAPI.resolve(targetPath, `${pathSafeNS}.js`));

          const parsedHeaderFile = JSON.parse(headerFile);

          return nsCodeAsync.then(nsCode => {
            parsedHeaderFile.nsCode = nsCode;
            return parsedHeaderFile;
          });
        })
        .catch(_ => undefined);
    },

    writeNSAsync: function(ns, {nsHeader, exports, nsCode}) {
      const pathSafeNS = makePathSafe(ns);
      const mkTargetPathAsync = mkdirAsync(targetPath);

      const headerContent = JSON.stringify({
        nsHeader: nsHeader.toJS(),
        exports: exports.map(v => v.set('value', undefined).set('expr', undefined))
      });

      return mkdirAsync(targetPath).then(_ => {
        const writeHeaderAsync = writeFileAsync(pathAPI.resolve(targetPath, `${pathSafeNS}.header.json`), headerContent);

        return writeHeaderAsync.then(_ => writeFileAsync(pathAPI.resolve(targetPath, `${pathSafeNS}.js`), nsCode));
      });
    }
  };
};
