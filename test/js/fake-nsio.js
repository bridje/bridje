module.exports = function({brjNSStrs = {}, jsNSStrs = {}}) {
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
      const nsStr = brjNSStrs[ns];
      if (nsStr === undefined) {
        return Promise.reject({error: 'ENOENT'});
      } else {
        return Promise.resolve(nsStr);
      }
    },

    resolveNSJSAsync: function(ns) {
      const nsJSStr = jsNSStrs[ns];
      if (nsJSStr === undefined) {
        return Promise.reject({error: 'ENOENT'});
      } else {
        return Promise.resolve(nsJSStr);
      }
    }
  };
};
