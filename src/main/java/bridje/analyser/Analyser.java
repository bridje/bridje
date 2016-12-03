package bridje.analyser;

import bridje.analyser.Expr.LetExpr.LetBinding;
import bridje.analyser.Expr.LocalVarExpr;
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
import static bridje.util.Pair.pair;

public class Analyser {

    static Expr<Void> analyse0(Env env, LocalEnv localEnv, Form form) {

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
                return new Expr.VectorExpr<>(form.range, null, form.forms.stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector()));
            }

            @Override
            public Expr<Void> visit(Form.SetForm form) {
                return new Expr.SetExpr<>(form.range, null, form.forms.stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector()));
            }

            private ListParser<Expr<Void>> exprParser(LocalEnv localEnv) {
                return oneOf(f -> success(analyse0(env, localEnv, f)));
            }

            final ListParser<Expr<Void>> exprParser = exprParser(localEnv);

            private ListParser<LetBinding<Void>> bindingParser(LocalEnv localEnv) {
                return SYMBOL_PARSER.bind(symForm ->
                    exprParser(localEnv).fmap(bindingExpr ->
                        new LetBinding<>(new LocalVar(symForm.sym), bindingExpr)));
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
                                    parseEnd(new Expr.DefExpr<>(form.range, null, symForm.sym, bodyExpr)))),

                            LIST_PARSER.bind(paramListForm ->
                                nestedListParser(
                                    paramListForm.forms,
                                    SYMBOL_PARSER.bind(nameForm ->
                                        manyOf(SYMBOL_PARSER.fmap(symForm -> new LocalVar(symForm.sym))).bind(localVars ->
                                            parseEnd(pair(nameForm.sym, localVars)))),

                                    nameAndVars -> exprParser(localEnv.withLocals(nameAndVars.right)).bind(bodyExpr ->
                                        parseEnd(new Expr.DefExpr<>(form.range, null, nameAndVars.left, new Expr.FnExpr<>(form.range, null, nameAndVars.right, bodyExpr)))))));
                    }

                    case "defdata": {
                        return anyOf(
                            SYMBOL_PARSER.bind(nameForm ->
                                parseConstructors(nameForm.sym, Empty.map()).bind(constructors ->
                                    parseEnd(new Expr.DefDataExpr<>(form.range, null, new DataType<>(null, nameForm.sym, Empty.vector(), constructors))))),
                            LIST_PARSER.bind(nameForm -> {
                                Map<Symbol, TypeVar> typeVarMapping = new HashMap<>();
                                return nestedListParser(nameForm.forms,
                                    SYMBOL_PARSER.bind(symForm ->
                                        manyOf(SYMBOL_PARSER).fmap(typeVarSyms -> {
                                            typeVarMapping.putAll(typeVarSyms.stream().collect(toPMap(f -> f.sym, f -> new TypeVar())));
                                            return pair(symForm.sym, typeVarSyms);
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
                            typeParser(LocalTypeEnv.EMPTY).bind(type ->
                                parseEnd(new Expr.TypeDefExpr<>(form.range, null, nameForm.sym, type))));
                    }

                    default:
                        return paramForms -> {
                            Var var = env.vars.get(firstSym);
                            if (var != null) {
                                PVector<Expr<Void>> paramExprs = paramForms.stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector());

                                if (var.fnMethod != null) {
                                    return success(pair(new Expr.VarCallExpr<>(form.range, null, var, paramExprs), Empty.vector()));
                                }
                            }

                            return success(pair(new Expr.CallExpr<>(form.range, null, paramForms.plus(0, firstSymbolForm).stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector())), Empty.vector()));
                        };
                }
            }

            private ListParser<PVector<DataTypeConstructor<Void>>> parseConstructors(Symbol name, PMap<Symbol, TypeVar> typeVarMapping) {
                LocalTypeEnv localTypeEnv = new LocalTypeEnv(HashTreePMap.singleton(name, new Type.DataTypeType(name, null)), typeVarMapping);

                return manyOf(anyOf(
                    SYMBOL_PARSER.fmap(symForm -> new ValueConstructor<Void>(null, symForm.sym)),
                    LIST_PARSER.bind(constructorForm -> nestedListParser(constructorForm.forms,
                        SYMBOL_PARSER.bind(cNameForm ->
                            manyOf(typeParser(localTypeEnv)).bind(paramTypes ->
                                parseEnd(new VectorConstructor<Void>(null, cNameForm.sym, paramTypes)))),

                        ListParser::pure))));
            }


            @Override
            public Expr<Void> visit(Form.ListForm form) {
                return SYMBOL_PARSER.bind(this::listFormParser).parse(form.forms).orThrow().left;
            }

            @Override
            public Expr<Void> visit(Form.SymbolForm form) {
                LocalVar localVar = localEnv.localVars.get(form.sym);
                if (localVar != null) {
                    return new LocalVarExpr<>(form.range, null, localVar);
                }

                Var var = env.vars.get(form.sym);

                if (var != null) {
                    return new Expr.GlobalVarExpr<>(form.range, null, var);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<Void> visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr<Void> analyse(Env env, Form form) {
        return analyse0(env, LocalEnv.EMPTY_ENV, form);
    }

}
