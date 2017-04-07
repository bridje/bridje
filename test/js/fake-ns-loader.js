module.exports = function(nsStrs) {
  return {
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
