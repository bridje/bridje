var Record = require('immutable').Record;

var Location = Record({line: 1, col: 1});
var Range = Record({start: null, end: null});

Location.prototype.toString = function() {
  return `L${this.line}C${this.col}`;
};

function range(start, end) {
  return new Range({
    start: start,
    end: end.update('col', col => col - 1)
  });
}

Range.prototype.toString = function() {
  return `${this.start}-${this.end}`;
};

var newLoc = new Location();

function moveLoc(ch, loc) {
  if (/[\r\n]/.test(ch)) {
    return loc
      .set('col', 1)
      .update('line', line => line + 1);
  } else {
    return loc.update('col', col => col + 1);
  }
};

module.exports = {
  Location: Location,
  Range: Range,
  range: range,
  newLoc: newLoc,
  moveLoc: moveLoc
};
