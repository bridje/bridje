module.exports = {
  entry: "./bridje/kernel/hello-world.brj",
  output: {
    path: __dirname + "/dist",
    filename: "bundle.js"
  },
  module: {
    rules: [{
      test: /\.brj$/,
      use: {
        loader: '../../src/js/bridje-loader'
      }
    }]
  }
};
