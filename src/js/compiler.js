const vm = require('vm');
const {List, Map, Set} = require('immutable');
const {Var, DataType, NSHeader} = require('./env');

function makeSafe(s) {
  return s.replace('-', '_');
};

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

    case 'jsGlobal':
      return `(${expr.path.join('.')})`;

    case 'match':
      // TODO this should work when the data types are in another NS
      const cases = expr.clauses.map(c => `case ${c.dataType.dataTypeName}: return ${compileExpr0(c.expr)};`);
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
const ${safeName} = (function (${params.join(', ')}) {return ${compileExpr0(expr.body)};})${call};
_exports = _exports.set('${name}', new _env.Var({ns: '${nsEnv.ns}', value: ${safeName}, name: '${name}', safeName: '${safeName}'}));
`
    };

  case 'defdata': {
    const safeName = makeSafe(expr.name);
    const recordName = `_constructor_${makeSafe(expr.name)}`;
    const dataTypeName = `_dataType_${makeSafe(expr.name)}`;
    const constructorVar = new Var({ns: nsEnv.ns, name: expr.name, safeName});
    const dataType = new DataType({name: expr.name, ns: nsEnv.ns, dataTypeName});
    const dataTypeStr = `new _env.DataType({name: '${expr.name}', ns: '${nsEnv.ns}', dataTypeName: '${dataTypeName}'})`;

    const {compiledRecord, compiledConstructor} = function() {
      switch (expr.type) {
      case 'value':
        return {
          compiledRecord: `_im.Record({})`,
          compiledConstructor: `new ${recordName}()`
        };

      case 'vector':
        const paramNames = expr.params.map(p => makeSafe(p));
        const recordParams = `{_params: _im.List([${paramNames.join(', ')}])}`;

        return {
          compiledRecord: `_im.Record({_params: null})`,
          compiledConstructor: `function(${paramNames.join(', ')}){return new ${recordName}(${recordParams})}`
        };

      case 'record':
        return {
          compiledRecord: `_im.Record({${expr.keys.map(k => `'${k}': undefined`).join(', ')}})`,
          compiledConstructor: `function(_r){return new ${recordName}(_r)}`
        };

      default:
        throw 'unknown defdata type';
      }
    }();

    return {
      nsEnv: nsEnv
        .setIn(['exports', constructorVar.name], constructorVar)
        .setIn(['dataTypes', expr.name], dataType),
      compiledForm: `
const ${dataTypeName} = ${dataTypeStr};
const ${recordName} = ${compiledRecord};
const ${safeName} = ${compiledConstructor};
${recordName}.prototype._brjType = ${dataTypeName};
_exports = _exports.set('${expr.name}', ${safeName});
_dataTypes = _dataTypes.set('${expr.name}', ${dataTypeName});
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

function compileNodeNS(nsEnv, compiledForms) {
  const refers = nsEnv.refers.entrySeq()
        .map(([name, referVar]) => `const ${referName(referVar.safeName)} = _refers.get('${referVar.name}').value;`);

  const subExprs = nsEnv.exports.valueSeq().flatMap(e => e.expr ? e.expr.subExprs() : []);

  const aliases = new Set(subExprs.filter(e => e.exprType == 'var' && e.alias != null))
        .map(ve => `const ${aliasName(ve.alias, ve.var.safeName)} = _aliases.get('${ve.alias}').exports.get('${ve.var.name}').value;`);

  return `

  (function(_env, _im) {
     return function(_nsEnv) {
       const _refers = _nsEnv.refers;
       const _aliases = _nsEnv.aliases;
       let _exports = _im.Map({}).asMutable();
       let _dataTypes = _im.Map({}).asMutable();

       ${refers.join('\n')}

       ${aliases.join('\n')}

       ${compiledForms.join('\n')}

       return _nsEnv.set('exports', _exports.asImmutable()).set('dataTypes', _dataTypes.asImmutable());
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

  const aliasedVarExprs = new Set(subExprs.filter(e => e.exprType == 'var' && e.alias != null));
  const aliases = aliasedVarExprs.map(ve => `const ${aliasName(ve.alias, ve.var.safeName)} = ${importNSVarName(ve.var.ns)}.get('${ve.var.name}').value;`);

  return `
  import _env from '../../../../src/js/env';
  import _im from 'immutable';

  let _exports = _im.Map({}).asMutable();
  let _dataTypes = _im.Map({}).asMutable();

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
