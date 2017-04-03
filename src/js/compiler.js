const vm = require('vm');
const {List, Map} = require('immutable');
const {Var} = require('./runtime');

function makeSafe(s) {
  return s.replace('-', '_');
};

function compileSymbol(sym) {
  return `new _runtime.Symbol({name: '${sym.name}'})`;
}

function compileExpr(env, nsEnv, expr) {
  var localNames = new Map({});
  var localNameCounts = new Map({});

  function localVarName(localVar) {
    const localName = localNames.get(localVar);

    if (localName != null) {
      return localName;
    } else {
      const localNameCount = (localNameCounts.get(localVar.name) || 0) + 1;
      localNameCounts = localNameCounts.set(localVar.name, localNameCount);
      const newLocalName = `${makeSafe(localVar.name)}_${localNameCount}`;

      localNames = localNames.set(localVar, newLocalName);
      return newLocalName;
    }
  }

  function compileExpr0(expr) {
    switch(expr.exprType) {
    case 'bool':
      return expr.bool.toString();
    case 'string':
      return JSON.stringify(expr.str);
    case 'int':
      return expr.int.toString();
    case 'float':
      return expr.float.toString();

    case 'vector':
      return `_im.List.of(${expr.exprs.map(compileExpr0).join(', ')})`;
    case 'set':
      return `_im.Set.of(${expr.exprs.map(compileExpr0).join(', ')})`;

    case 'record':
      // TODO we're going to want to intern the symbols
      const compiledEntries = expr.entries.map(entry => `[${compileSymbol(entry.key)}, ${compileExpr0(entry.value)}]`);

      return `_im.Map(_im.List.of(${compiledEntries.join(', ')}))`;

    case 'if':
      return `(${compileExpr0(expr.testExpr)} ? ${compileExpr0(expr.thenExpr)} : ${compileExpr0(expr.elseExpr)})`;

    case 'localVar':
      return localNames.get(expr.localVar);

    case 'let':
      const compiledBindings = expr.bindings.map(binding => `const ${localVarName(binding.localVar)} = ${compileExpr0(binding.expr)};`);
      return `(function () {${compiledBindings.join(' ')} return ${compileExpr0(expr.body)};})()`;

    case 'fn':
      const params = expr.params.map(localVarName);
      return `(function (${params.join(', ')}) {return ${compileExpr0(expr.body)};})`;

    case 'var':
      if (expr.var.ns == nsEnv.ns) {
        return expr.var.safeName;
      } else {
        throw 'global var exprs not supported yet';
      }

    case 'call':
      return `(${compileExpr0(expr.exprs.first())}(${expr.exprs.shift().map(compileExpr0).join(', ')}))`;

    default:
      throw 'compiler NIY';
    }
  }

  if (expr.exprType == 'def') {
    const name = expr.sym.name;
    const safeName = makeSafe(name);

    const params = expr.params ? expr.params.map(localVarName) : new List();
    const call = params.isEmpty() ? '()' : '';

    return {
      nsEnv: nsEnv.setIn(List.of('exports', name), new Var({ns: nsEnv.ns, name, safeName})),
      code: `\n const ${safeName} = (function (${params.join(', ')}) {return ${compileExpr0(expr.body)};})${call};\n`
    };

  } else {
    return {
      nsEnv,
      code: `(function() {return ${compileExpr0(expr)};})()`
    };
  }
}

function compileNS(env, nsEnv, content) {
  // TODO: requires in

  const exportEntries = nsEnv.exports
        .entrySeq()
        .map(([name, {safeName}]) => `['${name}', new _runtime.Var({ns: '${nsEnv.ns}', name: '${name}', value: ${safeName}, safeName: '${safeName}'})]`)
        .join(', ');

  const exports = `_im.Map(_im.List.of(${exportEntries}))`;

  return `
(function(_runtime, _im) {
  return {
    imports: {},
    f: function(_) {
      ${content}

      return {exports: ${exports}};
    }
  };
})`;
}

module.exports = {
  compileExpr, compileNS
};
