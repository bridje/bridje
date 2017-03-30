const vm = require('vm');
const {List} = require('immutable');
const {Var} = require('./runtime');

function makeSafe(s) {
  return s.replace('-', '_');
};

function compileExpr(env, nsEnv, expr) {
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

    case 'record':
    case 'let':
    default:
      throw 'compiler NIY';
    }
  }

  if (expr.exprType == 'def') {
    const name = expr.sym.name;
    const safeName = makeSafe(name);

    return {
      nsEnv: nsEnv.setIn(List.of('exports', name), new Var({safeName})),
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
        .map(([name, {safeName}]) => `['${name}', new _runtime.Var({value: ${safeName}, safeName: '${safeName}'})]`)
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
