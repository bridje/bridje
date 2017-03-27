function makeSafe(s) {
  return s;
};

function compileExpr(env, ns, expr) {
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
    const name = makeSafe(expr.sym.name);
    return `
const ${name} = (function () {${compileExpr0(expr.body)}})();
`;

  } else {
    return `
(function() {${compileExpr0(expr)}})()
`;
  }
}

module.exports = {
    compileExpr
};
