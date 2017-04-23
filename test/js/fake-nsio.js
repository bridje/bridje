function fakeNSResolver(fakeNSs) {
  return {
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
    }
  };
}
module.exports = {fakeNSResolver};
