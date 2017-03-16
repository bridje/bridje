var im = require('immutable');
var f = require('./form');

var ParseResult = im.Record({success: null, result: null, error: null});

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
  return new Parser(forms => this.parseForms(forms).then(res => {
    console.log(f(res.result) instanceof Parser);
    return f(res.result).parseForms(res.forms);
  }));
};

Parser.prototype.fmap = function (f) {
  return new Parser(forms => {
    let res = this.parseForms(forms);
    if (res.success) {
      return {result: f(res.result),
              forms: res.forms};
    } else {
      return res;
    }
  });
};

function pure(result) {
  return new Parser(forms => ({result, forms}));
}

function oneOf(formParser) {
  return new Parser(forms => {
    if(forms.isEmpty()) {
      return failResult('Expected form');
    } else {
      return formParser(forms.first()).fmap(result => {
        return {result, forms: forms.shift()};
      });
    }
  });
}

function manyOf(parser) {
  return new Parser(forms => {
    var result = im.List();
    while(!forms.isEmpty()) {
      let parseResult;
      ({result: parseResult, forms} = parser(forms));

      if(parseResult.success) {
        result = result.push(parseResult.result);
      } else {
        return parseResult;
      }
    }

    return successResult({result, forms});
  });
}

function anyOf(...parsers) {
  return new Parser(forms => {
    for (var i = 0; i < parsers.length; i++) {
      let result, resultForms;
      ({result, forms: resultForms} = parsers[i](forms));

      if (result.success) {
        return {result, forms: resultForms};
      }
    }

    return {result: failResult(), forms};
  });
}

function innerFormsParser(innerForms, innerParser, f) {
  // We parse the inner forms first, then call f to get back to a parser for the outer forms
  return new Parser(forms => {
    console.log('inner', innerParser.parseForms(innerForms));
    return innerParser.parseForms(innerForms).then(res => {
      console.log('res', f(res));
      return f(res).parseForms(forms);
    });
  });
}

function formTypeParser(FormType) {
  return oneOf(form => {
    if (form.form instanceof FormType) {
      return successResult(form);
    } else {
      return failResult(`${form} is not a ${FormType}`);
    }
  });
}

var SymbolParser = formTypeParser(f.SymbolForm);
var ListParser = formTypeParser(f.ListForm);
var VectorParser = formTypeParser(f.VectorForm);
var RecordParser = formTypeParser(f.RecordForm);

function isSymbol(sym) {
  return SymbolParser.fmap(symForm => {
    if (symForm.sym == sym) {
      return successResult(sym);
    } else {
      return failResult(`Expected symbol ${sym}`);
    }
  });
}

function parseForms(forms, parser) {
  return parser.parseForms(forms);
}

function parseForm(form, parser) {
  return parseForms(im.List.of(form), parser);
}

module.exports = {
  ParseResult, successResult, failResult,
  anyOf, oneOf, manyOf, isSymbol, pure,
  SymbolParser, ListParser, VectorParser, RecordParser,
  parseForms, parseForm, innerFormsParser
};
