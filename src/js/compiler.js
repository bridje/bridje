const vm = require('vm');
const {Range, List, Map, Set} = require('immutable');
const {Var, DataType, NSHeader, makeSafe, kernelExports} = require('./env');

function referName(s) {
  return '_refer_' + s;
}

function aliasName(alias, s) {
  return `_alias_${alias}_${s}`;
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
      const compiledEntries = expr.entries.map(entry => `['${entry.key}', ${compileExpr0(entry.value)}]`);

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

    case 'loop':
      const init = expr.bindings.map(binding => `let ${localVarName(binding.localVar)} = ${compileExpr0(binding.expr)};`);
      const body = compileExpr0(expr.body);
      return `(function() {${init.join('\n')} \n let _continue = true; while(_continue) {_continue = false; const _res = ${body}; if (!_continue) return _res;}})()`;

    case 'recur':
      const localVarNames = expr.bindings.map(binding => localVarName(binding.localVar));
      const exprs = expr.bindings.map(binding => compileExpr0(binding.expr));
      return `(function() {[${localVarNames.join(', ')}] = [${exprs.join(', ')}] \n _continue = true;})()`;


    case 'jsGlobal':
      return `(${expr.path.join('.')})`;

    case 'match':
      const cases = expr.clauses.map(c => {
        const dataTypeName = (function() {
          // TODO copy pasta
          if (c.var.ns == nsEnv.ns) {
            return c.var.safeName;
          } else {
            if (c.alias !== null) {
              return aliasName(c.alias, c.var.safeName);
            } else {
              return referName(c.var.safeName);
            }
          }
        })();

        return `case ${dataTypeName}: return ${compileExpr0(c.expr)};`;
      });
      return `(function () {switch (${compileExpr0(expr.expr)}._brjType) {${cases.join(' ')}}})()`;

    default:
      throw 'compiler NIY';
    }
  }

  switch (expr.exprType) {
  case 'def':
    const name = expr.sym.name;
    const safeName = makeSafe(name);

    const params = expr.params ? expr.params.map(localVarName) : new List();
    const call = params.isEmpty() ? '()' : '';

    return {
      nsEnv: nsEnv.setIn(['exports', name], new Var({ns: nsEnv.ns, expr, name, safeName})),
      compiledForm: `
const ${safeName} = (function ${safeName}(${params.join(', ')}) {return ${compileExpr0(expr.body)};})${call};
_exports = _exports.set('${name}', new _env.Var({ns: '${nsEnv.ns}', value: ${safeName}, name: '${name}', safeName: '${safeName}'}));
`
    };

  case 'defdata': {
    const safeName = makeSafe(expr.name);

    const {compiledRecord, compiledConstructor, accessors} = function() {
      switch (expr.type) {
      case 'value':
        return {
          compiledRecord: `_im.Record({})`,
          compiledConstructor: `new _record()`,
          accessors: List()
        };

      case 'vector':
        const paramNames = expr.params.map(p => makeSafe(p));
        const recordParams = `{_params: _im.List([${paramNames.join(', ')}])}`;

        return {
          compiledRecord: `_im.Record({_params: null})`,
          compiledConstructor: `function(${paramNames.join(', ')}){return new _record(${recordParams})}`,
          accessors: expr.params.zip(Range(0, expr.params.size)).map(([p, idx]) => {
            return {
              param: p,
              compiledAccessor: `(obj => obj._params.get(${idx}))`
            };
          })
        };

      case 'record':
        return {
          compiledRecord: `_im.Record({${expr.keys.map(k => `'${k}': undefined`).join(', ')}})`,
          compiledConstructor: `function(_r){return new _record(_r)}`,
          accessors: expr.keys.map(p => {
            return {
              param: p,
              compiledAccessor: `(obj => obj['${p}'])`
            };
          })
        };

      default:
        throw 'unknown defdata type';
      }
    }();

    const compiledAccessors = accessors.map(({param, compiledAccessor}) => {
      const accessorName = `${expr.name}.${param}`;
      const accessorSafeName = makeSafe(accessorName);
      nsEnv = nsEnv.setIn(['exports', accessorName], new Var({name: accessorName, ns: nsEnv.ns, safeName: accessorSafeName}));

      return `
const ${accessorSafeName} = ${compiledAccessor};
_exports = _exports.set('${accessorName}', new _env.Var({name: '${accessorName}', ns: '${nsEnv.ns}', safeName: '${accessorSafeName}', value: ${accessorSafeName}}))
`;});

    return {
      nsEnv: nsEnv
        .setIn(['exports', expr.name], new Var({name: expr.name, ns: nsEnv.ns, safeName})),

      compiledForm: `
${compiledAccessors.join('\n')}

const ${safeName} = (function() {
  const _record = ${compiledRecord};
  const _val = ${compiledConstructor};
  const _var = new _env.Var({name: '${expr.name}', ns: '${nsEnv.ns}', safeName: '${safeName}', value: _val});
  _record.prototype._brjType = _val;
  _exports = _exports.set('${expr.name}', _var);
  return _val;
})();
`
    };
  }

  default:
    return {
      nsEnv,
      compiledForm: `(function() {return ${compileExpr0(expr)};})()`
    };
  }
}

function aliasedVars(subExprs) {
  const aliasedExprs = new Set(subExprs.filter(e => e.exprType == 'var' && e.alias != null));
  const aliasedMatchExprs = new Set(subExprs.filter(e => e.exprType == 'match').flatMap(e => e.clauses).filter(e => e.alias != null));

  return aliasedExprs.union(aliasedMatchExprs).map(e => ({
    ns: e.var.ns,
    name: e.var.name,
    safeName: e.var.safeName,
    alias: e.alias
  }));
}

const importKernelExports = `const {${kernelExports.keySeq().map(k => `'${k}': ${makeSafe(k)}`).join(', ')}} = _env.kernelExports.map(v => v.value).toObject();`;

function compileNodeNS(nsEnv, compiledForms) {
  const refers = nsEnv.refers.entrySeq()
        .map(([name, referVar]) => `const ${referName(referVar.safeName)} = _refers.get('${referVar.name}').value;`);

  const subExprs = nsEnv.exports.valueSeq().flatMap(e => e.expr ? e.expr.subExprs() : []);

  const aliases = aliasedVars(subExprs).map(a => `const ${aliasName(a.alias, a.safeName)} = _aliases.get('${a.alias}').exports.get('${a.name}').value;`);
  const isKernel = nsEnv.ns == 'bridje.kernel';

  return `
  (function(_env, _im) {
     return function(_nsEnv) {
       const _refers = _nsEnv.refers;
       const _aliases = _nsEnv.aliases;
       let _exports = ${isKernel ? `_env.kernelExports` : `_im.Map({})`}.asMutable();
       ${isKernel ? importKernelExports : ''}

       ${refers.join('\n')}

       ${aliases.join('\n')}

       ${compiledForms.join('\n')}

       return _nsEnv.set('exports', _exports.asImmutable());
     }
   })
`;
}

function compileWebNS(env, nsEnv, compiledForms) {
  function importNSVarName(ns) {
    return `_import_${makeSafe(ns.replace(/\./g, '$'))}`;
  }

  const imports = Set(nsEnv.refers.valueSeq().map(referVar => referVar.ns))
        .union(Set(nsEnv.aliases.valueSeq().map(nsEnv => nsEnv.ns)))
        .map(importNS => `import ${importNSVarName(importNS)} from '${env.nsEnvs.get(importNS).brjFile}';`);

  const refers = nsEnv.refers.entrySeq()
        .map(([name, referVar]) => `const ${referName(referVar.safeName)} = ${importNSVarName(referVar.ns)}.get('${referVar.name}').value;`);

  const subExprs = nsEnv.exports.valueSeq().flatMap(e => e.expr ? e.expr.subExprs() : []);

  const aliases = aliasedVars(subExprs).map(a => `const ${aliasName(a.alias, a.safeName)} = ${importNSVarName(a.ns)}.get('${a.name}').value;`);

  const isKernel = nsEnv.ns == 'bridje.kernel';

  return `
  import _env from '../../../../src/js/env';
  import _im from 'immutable';

  let _exports = ${isKernel ? `_env.kernelExports` : `_im.Map({})`}.asMutable();
  ${isKernel ? importKernelExports : ''}

  ${imports.join('\n')}

  ${refers.join('\n')}
  ${aliases.join('\n')}

  ${compiledForms.join('\n')}

  export default _im.Map(_exports.asImmutable());
`;
}

module.exports = {
  compileExpr, compileNodeNS, compileWebNS
};
