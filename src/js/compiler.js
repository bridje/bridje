const vm = require('vm');
const {List, Map} = require('immutable');
const {Var} = require('./runtime');

function makeSafe(s) {
  return s.replace('-', '_');
};

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

    case 'if':
      return `(${compileExpr0(expr.testExpr)} ? ${compileExpr0(expr.thenExpr)} : ${compileExpr0(expr.elseExpr)})`;

    case 'let':
      const compiledBindings = expr.bindings.map(binding => `const ${localVarName(binding.localVar)} = ${compileExpr0(binding.expr)};`);
      return `(function () {${compiledBindings.join(' ')} return ${compileExpr0(expr.body)};})()`;

    case 'localVar':
      return localNames.get(expr.localVar);

    case 'var':
      if (expr.var.ns == nsEnv.ns) {
        return expr.var.safeName;
      } else {
        throw 'global var exprs not supported yet';
      }

    case 'record':
    default:
      throw 'compiler NIY';
    }
  }

  if (expr.exprType == 'def') {
    const name = expr.sym.name;
    const safeName = makeSafe(name);

    return {
      nsEnv: nsEnv.setIn(List.of('exports', name), new Var({ns: nsEnv.ns, name, safeName})),
      code: `\n const ${safeName} = (function () {return ${compileExpr0(expr.body)};})(); \n`
    };

  } else {
    return {
      nsEnv,
      code: `\n (function() {${compileExpr0(expr)}})() \n`
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
