package rho.types;

import org.pcollections.Empty;
import org.pcollections.PVector;
import rho.analyser.*;
import rho.analyser.ValueExpr.BoolExpr;
import rho.analyser.ValueExpr.StringExpr;
import rho.reader.Form;

import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.*;
import static rho.types.Type.VectorType.vectorType;

public class TypeChecker {

    private static ValueExpr<TypedExprData> typeValueExpr0(LocalTypeEnv localTypeEnv, ValueExpr<? extends Form> expr) {
        return expr.accept(new ValueExprVisitor<Form, ValueExpr<TypedExprData>>() {
            @Override
            public ValueExpr<TypedExprData> visit(BoolExpr<? extends Form> expr) {
                return new BoolExpr<>(new TypedExprData(BOOL_TYPE, expr.data), expr.value);
            }

            @Override
            public ValueExpr<TypedExprData> visit(StringExpr<? extends Form> expr) {
                return new StringExpr<>(new TypedExprData(STRING_TYPE, expr.data), expr.string);
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.IntExpr<? extends Form> expr) {
                return new ValueExpr.IntExpr<>(new TypedExprData(INT_TYPE, expr.data), expr.num);
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.VectorExpr<? extends Form> expr) {
                Type innerType = new TypeVar();
                TypeMapping mapping = TypeMapping.EMPTY;

                PVector<ValueExpr<TypedExprData>> exprs = Empty.vector();

                for (ValueExpr<? extends Form> el : expr.exprs) {
                    ValueExpr<TypedExprData> typedEl = typeValueExpr0(localTypeEnv, el);
                    exprs = exprs.plus(typedEl);
                    mapping = mapping.with(innerType.unify(typedEl.data.type));
                    innerType = innerType.apply(mapping);
                }

                return new ValueExpr.VectorExpr<>(new TypedExprData(vectorType(innerType), expr.data), exprs);
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.SetExpr<? extends Form> expr) {
                Type innerType = new TypeVar();
                TypeMapping mapping = TypeMapping.EMPTY;

                PVector<ValueExpr<TypedExprData>> exprs = Empty.vector();

                for (ValueExpr<? extends Form> el : expr.exprs) {
                    ValueExpr<TypedExprData> typedEl = typeValueExpr0(localTypeEnv, el);
                    exprs = exprs.plus(typedEl);
                    mapping = mapping.with(innerType.unify(typedEl.data.type));
                    innerType = innerType.apply(mapping);
                }

                return new ValueExpr.SetExpr<>(new TypedExprData(setType(innerType), expr.data), exprs);
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.CallExpr<? extends Form> expr) {
                PVector<? extends ValueExpr<? extends Form>> exprs = expr.exprs;
                ValueExpr<? extends Form> fnExpr = exprs.get(0);
                PVector<? extends ValueExpr<?>> params = exprs.minus(0);

                ValueExpr<TypedExprData> typedFirstParam = typeValueExpr0(localTypeEnv, fnExpr);
                Type firstParamType = typedFirstParam.data.type;


                if (firstParamType instanceof FnType) {
                    FnType fnType = (FnType) firstParamType;

                    PVector<Type> fnParamTypes = fnType.paramTypes;
                    if (fnParamTypes.size() == exprs.size() - 1) {
                        TypeMapping mapping = TypeMapping.EMPTY;
                        PVector<ValueExpr<TypedExprData>> typedExprs = Empty.vector();
                        for (int i = 0; i < fnParamTypes.size(); i++) {
                            TypeMapping mapping_ = mapping;
                            Type paramType = fnParamTypes.get(i).apply(mapping_);
                            ValueExpr<TypedExprData> typedExpr = typeValueExpr0(localTypeEnv, exprs.get(i + 1)).fmap(ted -> ted.fmapType(t -> t.apply(mapping_)));
                            typedExprs = typedExprs.plus(typedExpr);

                            mapping = mapping.with(paramType.unify(typedExpr.data.type));
                        }

                        TypeMapping mapping_ = mapping;

                        return new ValueExpr.CallExpr<TypedExprData>(new TypedExprData(fnType.returnType.apply(mapping), expr.data),
                            typedExprs.plus(0, typedFirstParam.fmap(ted -> ted.fmapType(t -> t.apply(mapping_)))));
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.VarCallExpr<? extends Form> expr) {
                Type varType = expr.var.type.instantiate();

                if (varType instanceof Type.FnType) {
                    FnType fnType = (FnType) varType;
                    TypeMapping mapping = TypeMapping.EMPTY;

                    if (expr.params.size() != fnType.paramTypes.size()) {
                        throw new UnsupportedOperationException();
                    }

                    PVector<ValueExpr<TypedExprData>> typedParams = Empty.vector();

                    for (int i = 0; i < expr.params.size(); i++) {
                        TypeMapping mapping_ = mapping;
                        Type paramType = fnType.paramTypes.get(i).apply(mapping_);
                        ValueExpr<TypedExprData> typedParam = typeValueExpr0(localTypeEnv, expr.params.get(i)).fmap(ted -> ted.fmapType(t -> t.apply(mapping_)));
                        typedParams = typedParams.plus(typedParam);

                        mapping = mapping.with(paramType.unify(typedParam.data.type));
                    }

                    return new ValueExpr.VarCallExpr<>(new TypedExprData(fnType.returnType.apply(mapping), expr.data), expr.var, typedParams);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.LetExpr<? extends Form> expr) {
                LocalTypeEnv localTypeEnv_ = localTypeEnv;

                PVector<ValueExpr.LetExpr.LetBinding<TypedExprData>> typedBindings = Empty.vector();

                for (ValueExpr.LetExpr.LetBinding<? extends Form> letBinding : expr.bindings) {
                    ValueExpr<TypedExprData> typedBindingExpr = typeValueExpr0(localTypeEnv_, letBinding.expr);
                    typedBindings = typedBindings.plus(new ValueExpr.LetExpr.LetBinding<>(letBinding.localVar, typedBindingExpr));
                    localTypeEnv_ = localTypeEnv_.with(letBinding.localVar, typedBindingExpr.data.type);
                }

                ValueExpr<TypedExprData> typedBody = typeValueExpr0(localTypeEnv_, expr.body);

                return new ValueExpr.LetExpr<>(new TypedExprData(typedBody.data.type, expr.data), typedBindings, typedBody);
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.IfExpr<? extends Form> expr) {
                ValueExpr<TypedExprData> typedTestExpr = typeValueExpr0(localTypeEnv, expr.testExpr);
                TypeMapping mapping = typedTestExpr.data.type.unify(BOOL_TYPE);

                TypeVar returnType = new TypeVar();
                ValueExpr<TypedExprData> typedThenExpr = typeValueExpr0(localTypeEnv, expr.thenExpr).fmap(ted -> ted.fmapType(t -> t.apply(mapping)));
                TypeMapping mapping_ = typedThenExpr.data.type.unify(returnType);

                ValueExpr<TypedExprData> typedElseExpr = typeValueExpr0(localTypeEnv, expr.elseExpr).fmap(ted -> ted.fmapType(t -> t.apply(mapping_)));
                TypeMapping mapping__ = typeValueExpr0(localTypeEnv, expr.elseExpr).data.type.unify(returnType);

                return new ValueExpr.IfExpr<>(new TypedExprData(returnType.apply(mapping__), expr.data), typedTestExpr, typedThenExpr, typedElseExpr);
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.LocalVarExpr<? extends Form> expr) {
                Type type = localTypeEnv.env.get(expr.localVar);
                if (type != null) {
                    return new ValueExpr.LocalVarExpr<>(new TypedExprData(type, expr.data), expr.localVar);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.GlobalVarExpr<? extends Form> expr) {
                return new ValueExpr.GlobalVarExpr<>(new TypedExprData(expr.var.type.instantiate(), expr.data), expr.var);
            }

            @Override
            public ValueExpr<TypedExprData> visit(ValueExpr.FnExpr<? extends Form> expr) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static ValueExpr<TypedExprData> typeValueExpr(ValueExpr<? extends Form> expr) {
        return typeValueExpr0(LocalTypeEnv.EMPTY_TYPE_ENV, expr);
    }

    public static Expr<TypedExprData> typeActionExpr(ActionExpr<? extends Form> expr) {
        return expr.accept(new ActionExprVisitor<Form, Expr<TypedExprData>>() {
            @Override
            public Expr<TypedExprData> visit(ActionExpr.DefExpr<? extends Form> expr) {
                return new ActionExpr.DefExpr<>(expr.sym, typeValueExpr(expr.body));
            }
        });
    }

    public static Expr<TypedExprData> type(Expr<Form> expr) {
        return expr.accept(new ExprVisitor<Form, Expr<TypedExprData>>() {
            @Override
            public Expr<TypedExprData> accept(ValueExpr<? extends Form> expr) {
                return typeValueExpr(expr);
            }

            @Override
            public Expr<TypedExprData> accept(ActionExpr<? extends Form> expr) {
                return typeActionExpr(expr);
            }
        });
    }

}