function fakeNSResolver(filesByExt) {
  return function(ns, ext) {
    const nsStr = (filesByExt[ext] || {})[ns];
    if (nsStr === undefined) {
      return Promise.reject({error: 'ENOENT'});
    } else {
      return Promise.resolve({brj: nsStr, brjFile: `/${ns.split('.').join('/')}.brj`});
    }
  };
}
module.exports = {fakeNSResolver};
