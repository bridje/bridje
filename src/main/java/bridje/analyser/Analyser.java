package bridje.analyser;

import bridje.analyser.Expr.LetExpr.LetBinding;
import bridje.analyser.Expr.LocalVarExpr;
import bridje.analyser.ParseException.DuplicateAliasException;
import bridje.analyser.ParseException.DuplicateReferException;
import bridje.analyser.ParseException.UnexpectedFormTypeException;
import bridje.reader.Form;
import bridje.reader.FormVisitor;
import bridje.runtime.*;
import bridje.util.Pair;
import org.pcollections.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static bridje.Util.fmap;
import static bridje.analyser.ListParser.*;
import static bridje.analyser.ListParser.ParseResult.fail;
import static bridje.analyser.ListParser.ParseResult.success;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.ns;
import static bridje.util.Pair.pair;

public class Analyser {

    static Function<Form, Expr> analyse0(Env env, NS currentNS, LocalEnv localEnv) {
        return form -> analyse0(env, currentNS, localEnv, form);
    }

    static Expr analyse0(Env env, NS currentNS, LocalEnv localEnv, Form form) {
        Function<Form, Expr> recur = analyse0(env, currentNS, localEnv);

        return form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr visit(Form.BoolForm form) {
                return new Expr.BoolExpr(form.range, form.value);
            }

            @Override
            public Expr visit(Form.StringForm form) {
                return new Expr.StringExpr(form.range, form.string);
            }

            @Override
            public Expr visit(Form.IntForm form) {
                return new Expr.IntExpr(form.range, form.num);
            }

            @Override
            public Expr visit(Form.VectorForm form) {
                return new Expr.VectorExpr(form.range, fmap(form.forms, recur));
            }

            @Override
            public Expr visit(Form.SetForm form) {
                return new Expr.SetExpr(form.range, fmap(form.forms, recur));
            }

            @Override
            public Expr visit(Form.RecordForm form) {
                throw new UnsupportedOperationException();
            }

            private ListParser<Expr> exprParser(LocalEnv localEnv) {
                return oneOf(f -> success(analyse0(env, currentNS, localEnv, f)));
            }

            final ListParser<Expr> exprParser = exprParser(localEnv);

            private ListParser<LetBinding> bindingParser(LocalEnv localEnv) {
                return SYMBOL_PARSER.bind(symForm ->
                    exprParser(localEnv).fmap(bindingExpr ->
                        new LetBinding(new LocalVar(symForm.sym), bindingExpr)));
            }

            private ParseResult<Pair<Expr, PVector<Form>>> parseVarCall(Var var, PVector<Form> paramForms) {
                return success(pair(new Expr.VarCallExpr(form.range, var, fmap(paramForms, recur)), Empty.vector()));
            }

            private ListParser<Expr> listFormParser(Form.SymbolForm firstSymbolForm) {
                Symbol firstSym = firstSymbolForm.sym;

                switch (firstSym.sym) {
                    case "let": {
                        return VECTOR_PARSER.bind(bindingForm ->
                            nestedListParser(
                                bindingForm.forms,
                                forms -> {
                                    List<LetBinding> letBindings = new LinkedList<>();
                                    LocalEnv localEnv_ = localEnv;

                                    while (!forms.isEmpty()) {
                                        ParseResult<Pair<LetBinding, PVector<Form>>> bindingParseResult = bindingParser(localEnv_).parse(forms);

                                        if (bindingParseResult instanceof ParseResult.Success) {
                                            Pair<LetBinding, PVector<Form>> result = ((ParseResult.Success<Pair<LetBinding, PVector<Form>>>) bindingParseResult).result;
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
                                            parseEnd(new Expr.LetExpr(form.range, localEnvAndBindings.right, bodyExpr)))));
                    }

                    case "if":
                        return
                            exprParser.bind(testExpr ->
                                exprParser.bind(thenExpr ->
                                    exprParser.bind(elseExpr ->
                                        parseEnd(new Expr.IfExpr(form.range, testExpr,
                                            thenExpr,
                                            elseExpr)))));


                    case "fn":
                        return LIST_PARSER.bind(paramListForm ->
                            nestedListParser(
                                paramListForm.forms,
                                manyOf(SYMBOL_PARSER.fmap(symForm -> new LocalVar(symForm.sym))),

                                localVars ->
                                    exprParser(localEnv.withLocals(localVars)).bind(bodyExpr ->
                                        parseEnd(new Expr.FnExpr(form.range, localVars, bodyExpr)))));

                    case "def": {
                        return anyOf(
                            SYMBOL_PARSER.bind(symForm ->
                                exprParser.bind(bodyExpr ->
                                    parseEnd(new Expr.DefExpr(form.range, fqSym(currentNS, symForm.sym), bodyExpr)))),

                            LIST_PARSER.bind(paramListForm ->
                                nestedListParser(
                                    paramListForm.forms,
                                    SYMBOL_PARSER.bind(nameForm ->
                                        manyOf(SYMBOL_PARSER.fmap(symForm -> new LocalVar(symForm.sym))).bind(localVars ->
                                            parseEnd(pair(fqSym(currentNS, nameForm.sym), localVars)))),

                                    nameAndVars -> exprParser(localEnv.withLocals(nameAndVars.right)).bind(bodyExpr ->
                                        parseEnd(new Expr.DefExpr(form.range, nameAndVars.left, new Expr.FnExpr(form.range, nameAndVars.right, bodyExpr)))))));
                    }

                    case "defdata": {
//                        return anyOf(
//                            SYMBOL_PARSER.bind(nameForm ->
//                                parseConstructors(fqSym(currentNS, nameForm.sym), Empty.map()).bind(constructors ->
//                                    parseEnd(new Expr.DefDataExpr(form.range, tag, new DataType<>(fqSym(currentNS, nameForm.sym), Empty.vector(), constructors))))),
//                            LIST_PARSER.bind(nameForm -> {
//                                Map<Symbol, TypeVar> typeVarMapping = new HashMap<>();
//                                return nestedListParser(nameForm.forms,
//                                    SYMBOL_PARSER.bind(symForm ->
//                                        manyOf(SYMBOL_PARSER).fmap(typeVarSyms -> {
//                                            typeVarMapping.putAll(typeVarSyms.stream().collect(toPMap(f -> f.sym, f -> new TypeVar())));
//                                            return pair(fqSym(currentNS, symForm.sym), typeVarSyms);
//                                        })),
//                                    dataTypeDef ->
//                                        parseConstructors(dataTypeDef.left, HashTreePMap.from(typeVarMapping)).fmap(constructors ->
//                                            new Expr.DefDataExpr(form.range, tag,
//                                                new DataType<>(dataTypeDef.left,
//                                                dataTypeDef.right.stream().map(s -> typeVarMapping.get(s.sym)).collect(toPVector()),
//                                                constructors))));
//                            }));
                        throw new UnsupportedOperationException();
                    }

                    case "defj": {
//                        return SYMBOL_PARSER.bind(nameForm ->
//                            SYMBOL_PARSER.bind(classNameForm ->
//                                SYMBOL_PARSER.bind(calleeNameForm ->
//                                    typeParser(env, LocalTypeEnv.EMPTY, currentNS).bind(type -> {
//                                        Class<?> c = env.resolveImport(currentNS, classNameForm.sym).orElse(null);
//                                        if (c == null) {
//                                            throw new UnsupportedOperationException();
//                                        }
//
//                                        try {
//                                            JCall call;
//                                            String calleeName = calleeNameForm.sym.sym;
//                                            if (calleeName.equals("new")) {
//                                                call = JCall.ConstructorCall.find(c, type);
//                                            } else if (calleeName.startsWith("-")) {
//                                                call = JCall.GetStaticFieldCall.find(c, calleeName.substring(1), type);
//                                            } else if (calleeName.startsWith(".")) {
//                                                call = JCall.InstanceMethodCall.find(c, calleeName.substring(1), type);
//                                            } else {
//                                                call = JCall.StaticMethodCall.find(c, calleeName, type);
//                                            }
//
//                                            return parseEnd(new Expr.DefJExpr(form.range, tag, fqSym(currentNS, nameForm.sym), call, type));
//                                        } catch (JCall.NoMatches | JCall.MultipleMatches e) {
//                                            throw new RuntimeException(e);
//                                        }
//                                    }))));
                        throw new UnsupportedOperationException();
                    }

                    case "ns": {
//                        return SYMBOL_PARSER.bind(nameForm ->
//                            anyOf(
//                                parseEnd(new Expr.NSExpr(form.range, ns(nameForm.sym.sym), Empty.map(), Empty.map(), Empty.map())),
//                                RECORD_PARSER.bind(optsForm -> {
//                                    PMap<Symbol, NS> aliases = Empty.map();
//                                    PMap<Symbol, FQSymbol> refers = Empty.map();
//                                    PMap<Symbol, Class<?>> imports = Empty.map();
//
//                                    for (Form.RecordForm.RecordEntryForm entry : optsForm.entries) {
//                                        switch (entry.key.sym) {
//                                            case "aliases":
//                                                if (!aliases.isEmpty()) {
//                                                    throw new ParseException.MultipleAliasesInNS(form);
//                                                }
//
//                                                if (entry.value instanceof Form.RecordForm) {
//                                                    Form.RecordForm aliasesForm = (Form.RecordForm) entry.value;
//                                                    Map<Symbol, NS> aliases_ = new HashMap<>();
//
//                                                    for (Form.RecordForm.RecordEntryForm aliasEntry : aliasesForm.entries) {
//                                                        if (!(aliasEntry.value instanceof Form.SymbolForm)) {
//                                                            throw new UnexpectedFormTypeException(aliasEntry.value);
//                                                        }
//
//                                                        Symbol alias = aliasEntry.key;
//                                                        NS ns = ns(((Form.SymbolForm) aliasEntry.value).sym.sym);
//
//                                                        if (aliases_.containsKey(alias)) {
//                                                            throw new DuplicateAliasException(form, alias);
//                                                        }
//
//                                                        if (!env.nsEnvs.containsKey(ns)) {
//                                                            throw new UnsupportedOperationException();
//                                                        }
//
//                                                        aliases_.put(alias, ns);
//
//                                                        // TODO should we do a cycle check here?
//                                                    }
//
//                                                    aliases = HashTreePMap.from(aliases_);
//                                                } else {
//                                                    throw new UnexpectedFormTypeException(entry.value);
//                                                }
//
//                                                break;
//
//                                            case "refers":
//                                                if (!refers.isEmpty()) {
//                                                    throw new ParseException.MultipleRefersInNS(form);
//                                                }
//
//                                                if (entry.value instanceof Form.RecordForm) {
//                                                    Map<Symbol, FQSymbol> refers_ = new HashMap<>();
//                                                    Form.RecordForm refersForm = (Form.RecordForm) entry.value;
//
//                                                    for (Form.RecordForm.RecordEntryForm referEntry : refersForm.entries) {
//                                                        NS referredNS = ns(referEntry.key.sym);
//                                                        // TODO should we do a cycle check here?
//
//                                                        if (!env.nsEnvs.containsKey(referredNS)) {
//                                                            throw new UnsupportedOperationException();
//                                                        }
//
//                                                        PSet<Symbol> availableSyms = HashTreePSet.from(env.nsEnvs.get(referredNS).declarations.keySet());
//
//                                                        if (!(referEntry.value instanceof Form.VectorForm)) {
//                                                            throw new UnexpectedFormTypeException(referEntry.value);
//                                                        }
//
//                                                        for (Form referredForm : ((Form.VectorForm) referEntry.value).forms) {
//                                                            if (!(referredForm instanceof Form.SymbolForm)) {
//                                                                throw new UnexpectedFormTypeException(referredForm);
//                                                            }
//
//                                                            Symbol referredSym = ((Form.SymbolForm) referredForm).sym;
//
//                                                            if (refers_.containsKey(referredSym)) {
//                                                                throw new DuplicateReferException(form, referredSym);
//                                                            }
//
//                                                            if (!availableSyms.contains(referredSym)) {
//                                                                throw new UnsupportedOperationException();
//                                                            }
//
//                                                            refers_.put(referredSym, fqSym(referredNS, referredSym));
//                                                        }
//                                                    }
//
//                                                    refers = HashTreePMap.from(refers_);
//                                                }
//
//                                                break;
//
//                                            case "imports":
//                                                if (!imports.isEmpty()) {
//                                                    throw new ParseException.MultipleImportsInNS(form);
//                                                }
//
//                                                if (entry.value instanceof Form.RecordForm) {
//                                                    Map<Symbol, Class<?>> imports_ = new HashMap<>();
//                                                    Form.RecordForm importsForm = (Form.RecordForm) entry.value;
//
//                                                    for (Form.RecordForm.RecordEntryForm importEntry : importsForm.entries) {
//                                                        if (!(importEntry.value instanceof Form.VectorForm)) {
//                                                            throw new UnexpectedFormTypeException(importEntry.value);
//                                                        }
//
//                                                        for (Form classNameForm : ((Form.VectorForm) importEntry.value).forms) {
//                                                            if (!(classNameForm instanceof Form.SymbolForm)) {
//                                                                throw new UnexpectedFormTypeException(classNameForm);
//                                                            }
//
//                                                            Symbol classNameSym = ((Form.SymbolForm) classNameForm).sym;
//
//                                                            if (imports_.containsKey(classNameSym)) {
//                                                                throw new DuplicateReferException(form, classNameSym);
//                                                            }
//
//                                                            Class<?> clazz;
//
//                                                            try {
//                                                                clazz = Class.forName(importEntry.key.sym + "." + classNameSym.sym);
//                                                            } catch (ClassNotFoundException e) {
//                                                                throw new UnsupportedOperationException();
//                                                            }
//
//                                                            imports_.put(classNameSym, clazz);
//                                                        }
//                                                    }
//
//                                                    imports = HashTreePMap.from(imports_);
//                                                }
//
//                                                break;
//
//                                            default:
//                                                throw new UnsupportedOperationException();
//                                        }
//                                    }
//
//                                    return parseEnd(new Expr.NSExpr(form.range, ns(nameForm.sym.sym), aliases, refers, imports));
//                                })));
                        throw new UnsupportedOperationException();
                    }

                    default:
                        return paramForms -> {
                            Var var = env.resolveVar(currentNS, firstSym).orElse(null);
                            if (var != null && var.fnMethod != null) {
                                return parseVarCall(var, paramForms);
                            }

                            return success(pair(new Expr.CallExpr(form.range, fmap(paramForms.plus(0, firstSymbolForm), recur)), Empty.vector()));
                        };
                }
            }

            private ListParser<PVector<DataTypeConstructor>> parseConstructors(FQSymbol name) {
//                return manyOf(anyOf(
//                    SYMBOL_PARSER.fmap(symForm -> new ValueConstructor(fqSym(currentNS, symForm.sym))),
//                    LIST_PARSER.bind(constructorForm -> nestedListParser(constructorForm.forms,
//                        SYMBOL_PARSER.bind(cNameForm ->
//                            manyOf(typeParser(env, localTypeEnv, currentNS)).bind(paramTypes ->
//                                parseEnd(new VectorConstructor(fqSym(currentNS, cNameForm.sym), paramNames)))),
//
//                        ListParser::pure))));
                throw new UnsupportedOperationException();
            }


            @Override
            public Expr visit(Form.ListForm form) {
//                return
//                    anyOf(
//                        SYMBOL_PARSER.bind(this::listFormParser),
//                        QSYMBOL_PARSER.bind(qsymForm -> (paramForms -> {
//                            Var var = env.resolveVar(currentNS, qsymForm.qsym).orElse(null);
//                            if (var != null) {
//                                return parseVarCall(var, paramForms);
//                            }
//
//                            throw new UnsupportedOperationException();
//                        })))
//
//                        .parse(form.forms).orThrow().left;
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.SymbolForm form) {
//                LocalVar localVar = localEnv.localVars.get(form.sym);
//                if (localVar != null) {
//                    return new LocalVarExpr(form.range, localVar);
//                }
//
//                return env.resolveVar(currentNS, form.sym)
//                    .map(var -> new Expr.GlobalVarExpr(form.range, var))
//                    .orElseThrow(UnsupportedOperationException::new);
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr analyse(Env env, NS currentNS, Form form) {
        return analyse0(env, currentNS, LocalEnv.EMPTY_ENV, form);
    }

}
