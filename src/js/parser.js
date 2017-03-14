var im = require('immutable');
var f = require('./form');

var ParseResult = im.Record({success: null, result: null, error: null});

function successResult(result) {
  return new ParseResult({success: true, result: result});
}

function failResult(error) {
  return new ParseResult({success: false, error: error});
}

ParseResult.prototype.bind = function(f) {
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

function pure(result) {
  return forms => ({result, forms});;
}

function oneOf(formParser) {
  return forms => {
    if(forms.isEmpty()) {
      return failResult('Expected form');
    } else {
      return formParser(forms.first()).fmap(result => {
        return {result, forms: forms.shift()};
      });
    }
  };
}

function manyOf(parser) {
  return forms => {
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
  };
}

function anyOf(...parsers) {
  return forms => {
    for (var i = 0; i < parsers.length; i++) {
      let result, resultForms;
      ({result, forms: resultForms} = parsers[i](forms));

      if (result.success) {
        return {result, forms: resultForms};
      }
    }

    return failResult();
  };
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

function parseForms(forms, parser) {
  return parser(forms);
}

function parseForm(form, parser) {
  return parseForms(im.List.of(form), parser);
}

module.exports = {
  ParseResult, successResult, failResult,
  anyOf, oneOf, manyOf,
  SymbolParser, ListParser, VectorParser, RecordParser,
  parseForms, parseForm
};
