const vm = require('vm');
const {List, Map, Set} = require('immutable');
const {Var, NSHeader} = require('./runtime');

function makeSafe(s) {
  return s.replace('-', '_');
};

function referName(s) {
  return '_refer_' + s;
}

function aliasName(alias, s) {
  return `_alias_${alias}_${s}`;
}

function internedSymName(sym) {
  return `_sym_${makeSafe(sym.name)}`;
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
      const compiledEntries = expr.entries.map(entry => `[${internedSymName(entry.key)}, ${compileExpr0(entry.value)}]`);

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
        if (expr.alias !== null) {
          return aliasName(expr.alias, expr.var.safeName);
        } else {
          return referName(expr.var.safeName);
        }
      }

    case 'call':
      return `(${compileExpr0(expr.exprs.first())}(${expr.exprs.shift().map(compileExpr0).join(', ')}))`;

    case 'jsGlobal':
      return `(${expr.path.join('.')})`;

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
      nsEnv: nsEnv.setIn(List.of('exports', name), new Var({ns: nsEnv.ns, expr, name, safeName})),
      code: `\n const ${safeName} = (function (${params.join(', ')}) {return ${compileExpr0(expr.body)};})${call};\n`
    };

  } else {
    return {
      nsEnv,
      code: `(function() {return ${compileExpr0(expr)};})()`
    };
  }
}

function compileNS(env, nsEnv, {hash, code}) {
  const refers = nsEnv.refers.entrySeq()
        .map(([name, referVar]) => `const ${referName(referVar.safeName)} = _refers.get('${referVar.name}').value;`)
        .join("\n");

  const subExprs = nsEnv.exports.valueSeq()
        .flatMap(e => e.expr.subExprs());

  const aliases =
        new Set(subExprs
                .filter(e => e.exprType == 'var' && e.alias != null))
        .map(ve => `const ${aliasName(ve.alias, ve.var.safeName)} = _aliases.get('${ve.alias}').exports.get('${ve.var.name}').value;`)
        .join('\n');

  const symbolInterns = new Set(subExprs
                                .filter(e => e.exprType == 'record')
                                .flatMap(r => r.entries)
                                .map(e => e.key))
        .map(sym => `const ${internedSymName(sym)} = new _runtime.Symbol({name: '${sym.name}'});`)
        .join('\n');

  const exportEntries = nsEnv.exports
        .entrySeq()
        .map(([name, {safeName}]) => `['${name}', new _runtime.Var({ns: '${nsEnv.ns}', name: '${name}', value: ${safeName}, safeName: '${safeName}'})]`)
        .join(', ');

  const exports = `_im.Map(_im.List.of(${exportEntries}))`;
  const nsHeaderRefers = `_im.Map(_im.List.of(${nsEnv.refers.entrySeq().map(([k, v]) => `['${k}', '${v.ns}']`).join(', ')}))`;
  const nsHeaderAliases = `_im.Map(_im.List.of(${nsEnv.aliases.entrySeq().map(([k, v]) => `['${k}', '${v.ns}']`).join(', ')}))`;

  return `
  (function(_runtime, _im) {
    return {
      hash: '${hash}',
      nsHeader: new _runtime.NSHeader({
          ns: '${nsEnv.ns}',
          refers: ${nsHeaderRefers},
          aliases: ${nsHeaderAliases}}),

      loadNS: function(_nsEnv) {
        const _refers = _nsEnv.refers;
        const _aliases = _nsEnv.aliases;
        ${refers}

        ${aliases}

        ${symbolInterns}

        ${code}

        return _nsEnv.set('exports', ${exports});
      }
    };
  })
`;
}

module.exports = {
  compileExpr, compileNS
};
