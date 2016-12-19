package bridje.analyser;

import bridje.analyser.Expr.LetExpr.LetBinding;
import bridje.analyser.Expr.LocalVarExpr;
import bridje.analyser.ParseException.DuplicateAliasException;
import bridje.analyser.ParseException.DuplicateReferException;
import bridje.analyser.ParseException.UnexpectedFormTypeException;
import bridje.reader.Form;
import bridje.reader.FormVisitor;
import bridje.runtime.*;
import bridje.runtime.DataTypeConstructor.ValueConstructor;
import bridje.runtime.DataTypeConstructor.VectorConstructor;
import bridje.types.Type;
import bridje.types.Type.TypeVar;
import bridje.util.Pair;
import org.pcollections.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static bridje.Util.toPMap;
import static bridje.Util.toPVector;
import static bridje.analyser.ListParser.*;
import static bridje.analyser.ListParser.ParseResult.fail;
import static bridje.analyser.ListParser.ParseResult.success;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.ns;
import static bridje.util.Pair.pair;

public class Analyser {

    static Expr<Void> analyse0(Env env, NS currentNS, LocalEnv localEnv, Form form) {

        return form.accept(new FormVisitor<Expr<Void>>() {
            @Override
            public Expr<Void> visit(Form.BoolForm form) {
                return new Expr.BoolExpr<>(form.range, null, form.value);
            }

            @Override
            public Expr<Void> visit(Form.StringForm form) {
                return new Expr.StringExpr<>(form.range, null, form.string);
            }

            @Override
            public Expr<Void> visit(Form.IntForm form) {
                return new Expr.IntExpr<>(form.range, null, form.num);
            }

            @Override
            public Expr<Void> visit(Form.VectorForm form) {
                return new Expr.VectorExpr<>(form.range, null, form.forms.stream().map(f -> analyse0(env, currentNS, localEnv, f)).collect(toPVector()));
            }

            @Override
            public Expr<Void> visit(Form.SetForm form) {
                return new Expr.SetExpr<>(form.range, null, form.forms.stream().map(f -> analyse0(env, currentNS, localEnv, f)).collect(toPVector()));
            }

            @Override
            public Expr<Void> visit(Form.MapForm mapForm) {
                return new Expr.MapExpr<>(mapForm.range, null, mapForm.entries.stream()
                    .map(e -> new Expr.MapExpr.MapEntryExpr<>(e.range,
                        analyse0(env, currentNS, localEnv, e.key),
                        analyse0(env, currentNS, localEnv, e.value)))
                    .collect(toPVector()));
            }

            @Override
            public Expr<Void> visit(Form.RecordForm form) {
                throw new UnsupportedOperationException();
            }

            private ListParser<Expr<Void>> exprParser(LocalEnv localEnv) {
                return oneOf(f -> success(analyse0(env, currentNS, localEnv, f)));
            }

            final ListParser<Expr<Void>> exprParser = exprParser(localEnv);

            private ListParser<LetBinding<Void>> bindingParser(LocalEnv localEnv) {
                return SYMBOL_PARSER.bind(symForm ->
                    exprParser(localEnv).fmap(bindingExpr ->
                        new LetBinding<>(new LocalVar(symForm.sym), bindingExpr)));
            }

            private ParseResult<Pair<Expr<Void>, PVector<Form>>> parseVarCall(Var var, PVector<Form> paramForms) {
                return success(pair(new Expr.VarCallExpr<>(form.range, null, var, paramForms.stream().map(f -> analyse0(env, currentNS, localEnv, f)).collect(toPVector())), Empty.vector()));
            }

            private ListParser<Expr<Void>> listFormParser(Form.SymbolForm firstSymbolForm) {
                Symbol firstSym = firstSymbolForm.sym;

                switch (firstSym.sym) {
                    case "let": {
                        return VECTOR_PARSER.bind(bindingForm ->
                            nestedListParser(
                                bindingForm.forms,
                                forms -> {
                                    List<LetBinding<Void>> letBindings = new LinkedList<>();
                                    LocalEnv localEnv_ = localEnv;

                                    while (!forms.isEmpty()) {
                                        ParseResult<Pair<LetBinding<Void>, PVector<Form>>> bindingParseResult = bindingParser(localEnv_).parse(forms);

                                        if (bindingParseResult instanceof ParseResult.Success) {
                                            Pair<LetBinding<Void>, PVector<Form>> result = ((ParseResult.Success<Pair<LetBinding<Void>, PVector<Form>>>) bindingParseResult).result;
                                            letBindings.add(result.left);
                                            forms = result.right;
                                            localEnv_ = localEnv_.withLocal(result.left.localVar);
                                        } else {
                                            return fail(((ParseResult.Fail) bindingParseResult).error);
                                        }
                                    }

                                    return success(pair(pair(localEnv_, TreePVector.from(letBindings)), Empty.vector()));
                                },

                                localEnvAndBindings ->
                                    exprParser(localEnvAndBindings.left)
                                        .bind(bodyExpr ->
                                            parseEnd(new Expr.LetExpr<>(form.range, null, localEnvAndBindings.right, bodyExpr)))));
                    }

                    case "if":
                        return
                            exprParser.bind(testExpr ->
                                exprParser.bind(thenExpr ->
                                    exprParser.bind(elseExpr ->
                                        parseEnd(new Expr.IfExpr<>(form.range, null,
                                            testExpr,
                                            thenExpr,
                                            elseExpr)))));


                    case "fn":
                        return LIST_PARSER.bind(paramListForm ->
                            nestedListParser(
                                paramListForm.forms,
                                manyOf(SYMBOL_PARSER.fmap(symForm -> new LocalVar(symForm.sym))),

                                localVars ->
                                    exprParser(localEnv.withLocals(localVars)).bind(bodyExpr ->
                                        parseEnd((Expr<Void>) new Expr.FnExpr<>(form.range, null, localVars, bodyExpr)))));

                    case "def": {
                        return anyOf(
                            SYMBOL_PARSER.bind(symForm ->
                                exprParser.bind(bodyExpr ->
                                    parseEnd(new Expr.DefExpr<>(form.range, null, fqSym(currentNS, symForm.sym), bodyExpr)))),

                            LIST_PARSER.bind(paramListForm ->
                                nestedListParser(
                                    paramListForm.forms,
                                    SYMBOL_PARSER.bind(nameForm ->
                                        manyOf(SYMBOL_PARSER.fmap(symForm -> new LocalVar(symForm.sym))).bind(localVars ->
                                            parseEnd(pair(fqSym(currentNS, nameForm.sym), localVars)))),

                                    nameAndVars -> exprParser(localEnv.withLocals(nameAndVars.right)).bind(bodyExpr ->
                                        parseEnd(new Expr.DefExpr<>(form.range, null, nameAndVars.left, new Expr.FnExpr<>(form.range, null, nameAndVars.right, bodyExpr)))))));
                    }

                    case "defdata": {
                        return anyOf(
                            SYMBOL_PARSER.bind(nameForm ->
                                parseConstructors(fqSym(currentNS, nameForm.sym), Empty.map()).bind(constructors ->
                                    parseEnd(new Expr.DefDataExpr<>(form.range, null, new DataType<>(null, fqSym(currentNS, nameForm.sym), Empty.vector(), constructors))))),
                            LIST_PARSER.bind(nameForm -> {
                                Map<Symbol, TypeVar> typeVarMapping = new HashMap<>();
                                return nestedListParser(nameForm.forms,
                                    SYMBOL_PARSER.bind(symForm ->
                                        manyOf(SYMBOL_PARSER).fmap(typeVarSyms -> {
                                            typeVarMapping.putAll(typeVarSyms.stream().collect(toPMap(f -> f.sym, f -> new TypeVar())));
                                            return pair(fqSym(currentNS, symForm.sym), typeVarSyms);
                                        })),
                                    dataTypeDef ->
                                        parseConstructors(dataTypeDef.left, HashTreePMap.from(typeVarMapping)).fmap(constructors ->
                                            new Expr.DefDataExpr<>(form.range,
                                                null, new DataType<>(null, dataTypeDef.left,
                                                dataTypeDef.right.stream().map(s -> typeVarMapping.get(s.sym)).collect(toPVector()),
                                                constructors))));
                            }));
                    }

                    case "::": {
                        return SYMBOL_PARSER.bind(nameForm ->
                            typeParser(env, LocalTypeEnv.EMPTY, currentNS).bind(type ->
                                parseEnd(new Expr.TypeDefExpr<>(form.range, null, fqSym(currentNS, nameForm.sym), type))));
                    }
                    case "defj": {
                        return SYMBOL_PARSER.bind(nameForm ->
                            SYMBOL_PARSER.bind(classNameForm ->
                                SYMBOL_PARSER.bind(calleeNameForm ->
                                    typeParser(env, LocalTypeEnv.EMPTY, currentNS).bind(type -> {
                                        Class<?> c = env.resolveImport(currentNS, classNameForm.sym).orElse(null);
                                        if (c == null) {
                                            throw new UnsupportedOperationException();
                                        }

                                        try {
                                            return parseEnd(new Expr.DefJExpr<>(form.range, null, fqSym(currentNS, nameForm.sym),
                                                JavaCall.StaticMethodCall.find(c, calleeNameForm.sym.sym, type), type));
                                        } catch (JavaCall.NoMatches | JavaCall.MultipleMatches e) {
                                            throw new RuntimeException(e);
                                        }
                                    }))));
                    }

                    case "ns": {
                        return SYMBOL_PARSER.bind(nameForm ->
                            anyOf(
                                parseEnd(new Expr.NSExpr<Void>(form.range, null, ns(nameForm.sym.sym), Empty.map(), Empty.map(), Empty.map())),
                                RECORD_PARSER.bind(optsForm -> {
                                    PMap<Symbol, NS> aliases = Empty.map();
                                    PMap<Symbol, FQSymbol> refers = Empty.map();
                                    PMap<Symbol, Class<?>> imports = Empty.map();

                                    for (Form.RecordForm.RecordEntryForm entry : optsForm.entries) {
                                        switch (entry.key.sym) {
                                            case "aliases":
                                                if (!aliases.isEmpty()) {
                                                    throw new ParseException.MultipleAliasesInNS(form);
                                                }

                                                if (entry.value instanceof Form.RecordForm) {
                                                    Form.RecordForm aliasesForm = (Form.RecordForm) entry.value;
                                                    Map<Symbol, NS> aliases_ = new HashMap<>();

                                                    for (Form.RecordForm.RecordEntryForm aliasEntry : aliasesForm.entries) {
                                                        if (!(aliasEntry.value instanceof Form.SymbolForm)) {
                                                            throw new UnexpectedFormTypeException(aliasEntry.value);
                                                        }

                                                        Symbol alias = aliasEntry.key;
                                                        NS ns = ns(((Form.SymbolForm) aliasEntry.value).sym.sym);

                                                        if (aliases_.containsKey(alias)) {
                                                            throw new DuplicateAliasException(form, alias);
                                                        }

                                                        if (!env.nsEnvs.containsKey(ns)) {
                                                            throw new UnsupportedOperationException();
                                                        }

                                                        aliases_.put(alias, ns);

                                                        // TODO should we do a cycle check here?
                                                    }

                                                    aliases = HashTreePMap.from(aliases_);
                                                } else {
                                                    throw new UnexpectedFormTypeException(entry.value);
                                                }

                                                break;

                                            case "refers":
                                                if (!refers.isEmpty()) {
                                                    throw new ParseException.MultipleRefersInNS(form);
                                                }

                                                if (entry.value instanceof Form.RecordForm) {
                                                    Map<Symbol, FQSymbol> refers_ = new HashMap<>();
                                                    Form.RecordForm refersForm = (Form.RecordForm) entry.value;

                                                    for (Form.RecordForm.RecordEntryForm referEntry : refersForm.entries) {
                                                        NS referredNS = ns(referEntry.key.sym);
                                                        // TODO should we do a cycle check here?

                                                        if (!env.nsEnvs.containsKey(referredNS)) {
                                                            throw new UnsupportedOperationException();
                                                        }

                                                        PSet<Symbol> availableSyms = HashTreePSet.from(env.nsEnvs.get(referredNS).declarations.keySet());

                                                        if (!(referEntry.value instanceof Form.VectorForm)) {
                                                            throw new UnexpectedFormTypeException(referEntry.value);
                                                        }

                                                        for (Form referredForm : ((Form.VectorForm) referEntry.value).forms) {
                                                            if (!(referredForm instanceof Form.SymbolForm)) {
                                                                throw new UnexpectedFormTypeException(referredForm);
                                                            }

                                                            Symbol referredSym = ((Form.SymbolForm) referredForm).sym;

                                                            if (refers_.containsKey(referredSym)) {
                                                                throw new DuplicateReferException(form, referredSym);
                                                            }

                                                            if (!availableSyms.contains(referredSym)) {
                                                                throw new UnsupportedOperationException();
                                                            }

                                                            refers_.put(referredSym, fqSym(referredNS, referredSym));
                                                        }
                                                    }

                                                    refers = HashTreePMap.from(refers_);
                                                }

                                                break;

                                            case "imports":
                                                if (!imports.isEmpty()) {
                                                    throw new ParseException.MultipleImportsInNS(form);
                                                }

                                                if (entry.value instanceof Form.RecordForm) {
                                                    Map<Symbol, Class<?>> imports_ = new HashMap<>();
                                                    Form.RecordForm importsForm = (Form.RecordForm) entry.value;

                                                    for (Form.RecordForm.RecordEntryForm importEntry : importsForm.entries) {
                                                        if (!(importEntry.value instanceof Form.VectorForm)) {
                                                            throw new UnexpectedFormTypeException(importEntry.value);
                                                        }

                                                        for (Form classNameForm : ((Form.VectorForm) importEntry.value).forms) {
                                                            if (!(classNameForm instanceof Form.SymbolForm)) {
                                                                throw new UnexpectedFormTypeException(classNameForm);
                                                            }

                                                            Symbol classNameSym = ((Form.SymbolForm) classNameForm).sym;

                                                            if (imports_.containsKey(classNameSym)) {
                                                                throw new DuplicateReferException(form, classNameSym);
                                                            }

                                                            Class<?> clazz;

                                                            try {
                                                                clazz = Class.forName(importEntry.key.sym + "." + classNameSym.sym);
                                                            } catch (ClassNotFoundException e) {
                                                                throw new UnsupportedOperationException();
                                                            }

                                                            imports_.put(classNameSym, clazz);
                                                        }
                                                    }

                                                    imports = HashTreePMap.from(imports_);
                                                }

                                                break;

                                            default:
                                                throw new UnsupportedOperationException();
                                        }
                                    }

                                    return parseEnd(new Expr.NSExpr<Void>(form.range, null, ns(nameForm.sym.sym), aliases, refers, imports));
                                })));
                    }

                    default:
                        return paramForms -> {
                            Var var = env.resolveVar(currentNS, firstSym).orElse(null);
                            if (var != null && var.fnMethod != null) {
                                return parseVarCall(var, paramForms);
                            }

                            return success(pair(new Expr.CallExpr<>(form.range, null, paramForms.plus(0, firstSymbolForm).stream().map(f -> analyse0(env, currentNS, localEnv, f)).collect(toPVector())), Empty.vector()));
                        };
                }
            }

            private ListParser<PVector<DataTypeConstructor<Void>>> parseConstructors(FQSymbol name, PMap<Symbol, TypeVar> typeVarMapping) {
                LocalTypeEnv localTypeEnv = new LocalTypeEnv(HashTreePMap.singleton(name.symbol, new Type.DataTypeType(name, null)), typeVarMapping);

                return manyOf(anyOf(
                    SYMBOL_PARSER.fmap(symForm -> new ValueConstructor<Void>(null, fqSym(currentNS, symForm.sym))),
                    LIST_PARSER.bind(constructorForm -> nestedListParser(constructorForm.forms,
                        SYMBOL_PARSER.bind(cNameForm ->
                            manyOf(typeParser(env, localTypeEnv, currentNS)).bind(paramTypes ->
                                parseEnd(new VectorConstructor<Void>(null, fqSym(currentNS, cNameForm.sym), paramTypes)))),

                        ListParser::pure))));
            }


            @Override
            public Expr<Void> visit(Form.ListForm form) {
                return
                    anyOf(
                        SYMBOL_PARSER.bind(this::listFormParser),
                        QSYMBOL_PARSER.bind(qsymForm -> (paramForms -> {
                            Var var = env.resolveVar(currentNS, qsymForm.qsym).orElse(null);
                            if (var != null) {
                                return parseVarCall(var, paramForms);
                            }

                            throw new UnsupportedOperationException();
                        })))

                        .parse(form.forms).orThrow().left;
            }

            @Override
            public Expr<Void> visit(Form.SymbolForm form) {
                LocalVar localVar = localEnv.localVars.get(form.sym);
                if (localVar != null) {
                    return new LocalVarExpr<>(form.range, null, localVar);
                }

                return env.resolveVar(currentNS, form.sym)
                    .map(var -> new Expr.GlobalVarExpr<Void>(form.range, null, var))
                    .orElseThrow(UnsupportedOperationException::new);
            }

            @Override
            public Expr<Void> visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr<Void> analyse(Env env, NS currentNS, Form form) {
        return analyse0(env, currentNS, LocalEnv.EMPTY_ENV, form);
    }

}
