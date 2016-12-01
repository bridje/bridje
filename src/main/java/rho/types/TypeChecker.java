package rho.types;

import org.pcollections.Empty;
import org.pcollections.PVector;
import rho.analyser.Expr;
import rho.analyser.Expr.BoolExpr;
import rho.analyser.Expr.StringExpr;
import rho.analyser.ExprVisitor;
import rho.analyser.LocalVar;
import rho.runtime.ConstructorVisitor;
import rho.runtime.DataType;
import rho.runtime.DataTypeConstructor;

import static rho.Util.toPVector;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.*;
import static rho.types.Type.VectorType.vectorType;

public class TypeChecker {

    private TypeMapping mapping;

    private TypeChecker(TypeMapping mapping) {
        this.mapping = mapping;
    }

    private Expr<Type> typeExpr0(LocalTypeEnv localTypeEnv, Expr<?> expr) {
        return expr.accept(new ExprVisitor<Object, Expr<Type>>() {
            @Override
            public Expr<Type> visit(BoolExpr<?> expr) {
                return new BoolExpr<>(expr.range, BOOL_TYPE, expr.value);
            }

            @Override
            public Expr<Type> visit(StringExpr<?> expr) {
                return new StringExpr<>(expr.range, STRING_TYPE, expr.string);
            }

            @Override
            public Expr<Type> visit(Expr.IntExpr<?> expr) {
                return new Expr.IntExpr<>(expr.range, INT_TYPE, expr.num);
            }

            @Override
            public Expr<Type> visit(Expr.VectorExpr<?> expr) {
                Type innerType = new TypeVar();

                PVector<Expr<Type>> exprs = Empty.vector();

                for (Expr<?> el : expr.exprs) {
                    Expr<Type> typedEl = typeExpr0(localTypeEnv, el);
                    exprs = exprs.plus(typedEl);
                    mapping = mapping.with(innerType.unify(typedEl.type));
                    innerType = innerType.apply(mapping);
                }

                return new Expr.VectorExpr<>(expr.range, vectorType(innerType), exprs);
            }

            @Override
            public Expr<Type> visit(Expr.SetExpr<?> expr) {
                Type innerType = new TypeVar();

                PVector<Expr<Type>> exprs = Empty.vector();

                for (Expr<?> el : expr.exprs) {
                    Expr<Type> typedEl = typeExpr0(localTypeEnv, el);
                    exprs = exprs.plus(typedEl);
                    mapping = mapping.with(innerType.unify(typedEl.type));
                    innerType = innerType.apply(mapping);
                }

                return new Expr.SetExpr<>(expr.range, setType(innerType), exprs);
            }

            @Override
            public Expr<Type> visit(Expr.CallExpr<?> expr) {
                PVector<? extends Expr<?>> exprs = expr.exprs;
                Expr<?> fnExpr = exprs.get(0);

                Expr<Type> typedFirstParam = typeExpr0(localTypeEnv, fnExpr);
                Type firstParamType = typedFirstParam.type;


                if (firstParamType instanceof FnType) {
                    FnType fnType = (FnType) firstParamType;

                    PVector<Type> fnParamTypes = fnType.paramTypes;
                    if (fnParamTypes.size() == exprs.size() - 1) {
                        PVector<Expr<Type>> typedExprs = Empty.vector();
                        for (int i = 0; i < fnParamTypes.size(); i++) {
                            TypeMapping mapping_ = mapping;
                            Type paramType = fnParamTypes.get(i).apply(mapping_);
                            Expr<Type> typedExpr = typeExpr0(localTypeEnv, exprs.get(i + 1)).fmapType(t -> t.apply(mapping_));
                            mapping = mapping.with(paramType.unify(typedExpr.type));

                            typedExprs = typedExprs.plus(typedExpr);

                        }

                        TypeMapping mapping_ = mapping;

                        return new Expr.CallExpr<>(expr.range, fnType.returnType.apply(mapping),
                            typedExprs.plus(0, typedFirstParam.fmapType(t -> t.apply(mapping_))));
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<Type> visit(Expr.VarCallExpr<?> expr) {
                Type varType = expr.var.inferredType.instantiate();

                if (varType instanceof Type.FnType) {
                    FnType fnType = (FnType) varType;

                    if (expr.params.size() != fnType.paramTypes.size()) {
                        throw new UnsupportedOperationException();
                    }

                    PVector<Expr<Type>> typedParams = Empty.vector();

                    for (int i = 0; i < expr.params.size(); i++) {
                        TypeMapping mapping_ = mapping;
                        Type paramType = fnType.paramTypes.get(i).apply(mapping_);
                        Expr<Type> typedParam = typeExpr0(localTypeEnv, expr.params.get(i)).fmapType(t -> t.apply(mapping_));
                        typedParams = typedParams.plus(typedParam);

                        mapping = mapping.with(paramType.unify(typedParam.type));
                    }

                    return new Expr.VarCallExpr<>(expr.range, fnType.returnType.apply(mapping), expr.var, typedParams);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<Type> visit(Expr.LetExpr<?> expr) {
                LocalTypeEnv localTypeEnv_ = localTypeEnv;

                PVector<Expr.LetExpr.LetBinding<Type>> typedBindings = Empty.vector();

                for (Expr.LetExpr.LetBinding<?> letBinding : expr.bindings) {
                    Expr<Type> typedBindingExpr = typeExpr0(localTypeEnv_, letBinding.expr);
                    typedBindings = typedBindings.plus(new Expr.LetExpr.LetBinding<>(letBinding.localVar, typedBindingExpr));
                    localTypeEnv_ = localTypeEnv_.with(letBinding.localVar, typedBindingExpr.type);
                }

                Expr<Type> typedBody = typeExpr0(localTypeEnv_, expr.body);

                return new Expr.LetExpr<>(expr.range, typedBody.type, typedBindings, typedBody);
            }

            @Override
            public Expr<Type> visit(Expr.IfExpr<?> expr) {
                Expr<Type> typedTestExpr = typeExpr0(localTypeEnv, expr.testExpr);
                mapping = mapping.with(typedTestExpr.type.unify(BOOL_TYPE));

                TypeVar returnType = new TypeVar();
                Expr<Type> typedThenExpr = typeExpr0(localTypeEnv, expr.thenExpr).fmapType(t -> t.apply(mapping));
                mapping = mapping.with(typedThenExpr.type.unify(returnType));

                Expr<Type> typedElseExpr = typeExpr0(localTypeEnv, expr.elseExpr).fmapType(t -> t.apply(mapping));
                mapping = mapping.with(typeExpr0(localTypeEnv, expr.elseExpr).type.unify(returnType));

                return new Expr.IfExpr<>(expr.range, returnType.apply(mapping), typedTestExpr, typedThenExpr, typedElseExpr);
            }

            @Override
            public Expr<Type> visit(Expr.LocalVarExpr<?> expr) {
                Type type = localTypeEnv.env.get(expr.localVar);
                if (type != null) {
                    return new Expr.LocalVarExpr<>(expr.range, type, expr.localVar);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<Type> visit(Expr.GlobalVarExpr<?> expr) {
                return new Expr.GlobalVarExpr<>(expr.range, expr.var.inferredType.instantiate(), expr.var);
            }

            @Override
            public Expr<Type> visit(Expr.FnExpr<?> expr) {
                LocalTypeEnv localTypeEnv_ = localTypeEnv;

                for (LocalVar param : expr.params) {
                    localTypeEnv_ = localTypeEnv_.with(param, new TypeVar());
                }

                final LocalTypeEnv bodyLocalTypeEnv = localTypeEnv_;

                TypeVar returnType = new TypeVar();
                Expr<Type> typedBody = typeExpr0(localTypeEnv_, expr.body);
                Type bodyType = typedBody.type;
                mapping = mapping.with(bodyType.unify(returnType));

                return new Expr.FnExpr<>(expr.range, fnType(
                    expr.params.stream().map(p -> bodyLocalTypeEnv.env.get(p).apply(mapping)).collect(toPVector()),
                    bodyType.apply(mapping)),
                    expr.params,
                    typedBody.fmapType(t -> t.apply(mapping)));
            }

            @Override
            public Expr<Type> visit(Expr.DefExpr<?> expr) {
                return new Expr.DefExpr<>(expr.range, ENV_IO, expr.sym, typeExpr0(localTypeEnv, expr.body));
            }

            @Override
            public Expr<Type> visit(Expr.TypeDefExpr<?> expr) {
                // at some point we'll have to check the types that the user provides us with.
                // today is not that day, though.
                return new Expr.TypeDefExpr<>(expr.range, ENV_IO, expr.sym, expr.typeDef);
            }

            @Override
            public Expr<Type> visit(Expr.DefDataExpr<?> expr) {
                DataType<?> dataType = expr.dataType;
                Type dataTypeType = new DataTypeType(dataType.sym, null);

                return new Expr.DefDataExpr<>(expr.range, ENV_IO,
                    new DataType<>(
                        dataTypeType,
                        dataType.sym,
                        Empty.vector(), dataType.constructors.stream()
                            .map(c -> c.accept(new ConstructorVisitor<Object, DataTypeConstructor<Type>>() {

                                @Override
                                public DataTypeConstructor<Type> visit(DataTypeConstructor.VectorConstructor<?> constructor) {
                                    return new DataTypeConstructor.VectorConstructor<>(fnType(constructor.paramTypes, dataTypeType), constructor.sym, constructor.paramTypes);
                                }

                                @Override
                                public DataTypeConstructor<Type> visit(DataTypeConstructor.ValueConstructor<?> constructor) {
                                    return new DataTypeConstructor.ValueConstructor<>(dataTypeType, constructor.sym);
                                }
                            })).collect(toPVector())));
            }
        });
    }

    public static Expr<Type> typeExpr(Expr<Void> expr) {
        return new TypeChecker(TypeMapping.EMPTY).typeExpr0(LocalTypeEnv.EMPTY_TYPE_ENV, expr);
    }

}