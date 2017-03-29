const vm = require('vm');
const {List} = require('immutable');

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
      nsEnv: nsEnv.setIn(List.of('exports', name), {safeName}),
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

  const varEntries = nsEnv.exports
        .entrySeq()
        .map(([name, {safeName}]) => `['${name}', ${safeName}]`)
        .join(', ');

  const vars = `_im.Map(_im.List.of(${varEntries}))`;

  return `
(function(_brjNS, _im) {
  return new _brjNS({
    imports: {},
    f: function(_) {
      ${content}

      return {vars: ${vars}};
    }
  });
})`;
}

module.exports = {
  compileExpr, compileNS
};
