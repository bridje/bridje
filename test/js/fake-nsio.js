module.exports = function(filesByExt) {
  const writtenNSs = {};

  return {
    writtenNS: function(ns) {
      return writtenNSs[ns];
    },

    writeNSAsync: function(ns, str) {
      writtenNSs[ns] = str;
      return Promise.resolve();
    },

    resolveNSAsync: function(ns, ext) {
      const nsStr = (filesByExt[ext] || {})[ns];
      if (nsStr === undefined) {
        return Promise.reject({error: 'ENOENT'});
      } else {
        return Promise.resolve(nsStr);
      }
    }
  };
};
