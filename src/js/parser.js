var {List, Record} = require('immutable');
var f = require('./form');

var ParseResult = Record({success: null, result: null, error: null});

function successResult(result) {
  return new ParseResult({success: true, result: result});
}

function failResult(error) {
  return new ParseResult({success: false, error: error});
}

ParseResult.prototype.then = function(f) {
  if(this.success) {
    return f(this.result);
  } else {
    return this;
  }
};

ParseResult.prototype.fmap = function(f) {
  if(this.success) {
    return successResult(f(this.result));
  } else {
    return this;
  }
};

ParseResult.prototype.accept = function(onSuccess, onFail) {
  if(this.success) {
    return onSuccess(this.result);
  } else {
    return onFail(this.error);
  }
};

ParseResult.prototype.orThrow = function() {
  if (this.success) {
    return this.result;
  } else {
    throw this.error;
  }
};

function Parser(parseForms) {
  this.parseForms = parseForms;
}

Parser.prototype.then = function (f) {
  return new Parser(forms => {
    let res = this.parseForms(forms);
    if (res.result.success) {
      return f(res.result.result).parseForms(res.forms);
    } else {
      return res;
    }
  });
};

Parser.prototype.fmap = function (f) {
  return new Parser(forms => {
    let res = this.parseForms(forms);
    return {
      result: res.result.fmap(f),
      forms: res.forms
    };
  });
};

function pure(result) {
  return new Parser(forms => ({result, forms}));
}

function oneOf(formParser) {
  return new Parser(forms => {
    if(forms.isEmpty()) {
      return {
        result: failResult('Expected form'),
        forms
      };
    } else {
      return {
        result: formParser(forms.first()),
        forms: forms.shift()
      };
    }
  });
}

function atLeastOneOf(parser) {
  return new Parser(forms => {
    var result = List();
    while(!forms.isEmpty()) {
      let parseResult;
      const unmatchedForms =  forms;

      ({result: parseResult, forms} = parser.parseForms(forms));

      if(parseResult.success) {
        result = result.push(parseResult.result);
      } else {
        if (result.isEmpty()) {
          return {result: parseResult, forms};
        } else {
          return {result, forms: unmatchedForms};
        }
      }
    }

    return {result: successResult(result), forms};
  });
}

function anyOf(...parsers) {
  return new Parser(forms => {
    let fails = [];

    for (var i = 0; i < parsers.length; i++) {
      let result, resultForms;
      ({result, forms: resultForms} = parsers[i].parseForms(forms));

      if (result.success) {
        return {result, forms: resultForms};
      } else {
        fails.push(result.error);
      }
    }

    return {result: failResult(fails), forms};
  });
}

function parseEnd(result) {
  return new Parser(forms => {
    if (forms.isEmpty()) {
      return {
        result: successResult(result),
        forms
      };
    } else {
      return {
        result: failResult(`Unexpected forms: ${forms}`),
        forms
      };
    }
  });
}

function innerFormsParser(innerForms, innerParser) {
  return new Parser(forms => ({result: innerParser.parseForms(innerForms).result, forms}));
}

function formTypeParser(formType) {
  return oneOf(form => {
    if (form.formType === formType) {
      return successResult(form);
    } else {
      return failResult(`${form} is not a ${formType}`);
    }
  });
}

var SymbolParser = formTypeParser('symbol');
var ListParser = formTypeParser('list');
var VectorParser = formTypeParser('vector');
var RecordParser = formTypeParser('record');

function isSymbol(sym) {
  return SymbolParser.then(symForm => {
    if (symForm.sym.equals(sym)) {
      return pure(successResult(sym));
    } else {
      return pure(failResult(`Expected symbol ${sym}`));
    }
  });
}

function parseForms(forms, parser) {
  return parser.parseForms(forms).result;
}

function parseForm(form, parser) {
  return parseForms(List.of(form), parser);
}

module.exports = {
  ParseResult, successResult, failResult,
  anyOf, oneOf, atLeastOneOf, isSymbol, pure,
  innerFormsParser, parseEnd,
  Parser, SymbolParser, ListParser, VectorParser, RecordParser,
  parseForms, parseForm
};
