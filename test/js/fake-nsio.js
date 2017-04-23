function fakeNSResolver(fakeFiles) {
  return {
    resolveNSAsync: function(ns) {
      const {brj} = fakeFiles[ns] || {};
      if (brj === undefined) {
        return Promise.reject({error: 'ENOENT'});
      } else {
        return Promise.resolve({brj, brjFile: `/${ns.split('.').join('/')}.brj`});
      }
    },

    resolveCachedNSAsync: function(ns) {
      return Promise.resolve(undefined);
    }
  };
}
module.exports = {fakeNSResolver};
