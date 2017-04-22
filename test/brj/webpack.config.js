const path = require('path');

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
        loader: '../../src/js/bridje-loader',
        options: {
          projectPaths: [__dirname, path.resolve(__dirname, '../../src/brj')]
        }
      }
    }]
  }
};
