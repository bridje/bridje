module.exports = function (fakeNSs) {
  let writtenNSs = {};

  return {
    writtenNSs,
    resolveNSAsync: function(ns) {
      const {brj} = fakeNSs[ns] || {};
      if (brj === undefined) {
        return Promise.reject({error: 'ENOENT'});
      } else {
        return Promise.resolve({brj, brjFile: `/${ns.split('.').join('/')}.brj`});
      }
    },

    resolveCachedNSAsync: function(ns, hash) {
      const {cachedNS, hashValid} = fakeNSs[ns] || {};

      if (cachedNS && (!hashValid || hashValid(hash))) {
        return Promise.resolve(cachedNS);
      } else {
        return Promise.resolve(undefined);
      }
    },

    writeNSAsync: function(ns, cachedNS) {
      writtenNSs[ns] = cachedNS;
      return Promise.resolve(undefined);
    }
  };
};
