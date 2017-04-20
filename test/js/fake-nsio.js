function fakeNSResolver(filesByExt) {
  return function(ns, ext) {
    const nsStr = (filesByExt[ext] || {})[ns];
    if (nsStr === undefined) {
      return Promise.reject({error: 'ENOENT'});
    } else {
      return Promise.resolve(nsStr);
    }
  };
}
module.exports = {fakeNSResolver};
