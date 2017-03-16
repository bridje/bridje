var ana = require('../../src/js/analyser');
var reader = require('../../src/js/reader');

describe('analyser', () => {
  it('reads an NS form', () => {
    console.log(ana.analyseNSForm(null, 'bridje.kernel', reader.readForms(`(nsa bridje.kernel)`).first()));
  });
});
