module.exports = function(nsStrs) {
  const writtenNSs = {};

  return {
    writtenNS: function(ns) {
      return writtenNSs[ns];
    },

    writeNSAsync: function(ns, str) {
      writtenNSs[ns] = str;
      return Promise.resolve();
    },

    resolveNSAsync: function(ns) {
      const nsStr = nsStrs[ns];
      if (nsStr === undefined) {
        return Promise.reject({error: 'ENOENT'});
      } else {
        return Promise.resolve(nsStr);
      }
    }
  };
};
