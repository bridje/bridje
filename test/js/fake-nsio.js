module.exports = function (fakeNSs) {
  let writtenNSs = {};

  return {
    writtenNSs,
    resolveNSAsync: function(ns) {
      const {brj} = fakeNSs[ns] || {};
      if (brj === undefined) {
        return Promise.reject({error: 'ENOENT'});
      } else {
        return Promise.resolve({brj, brjFile: `/${ns.replace(/\./g, '/')}.brj`});
      }
    },

    resolveCachedNSAsync: function(ns, nsHash) {
      const {cachedNS, nsHash: expectedNSHash, hashValid} = fakeNSs[ns] || {};

      if (cachedNS && (!expectedNSHash || nsHash == expectedNSHash)) {
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
