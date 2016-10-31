package rho.types;

import org.pcollections.PVector;
import rho.analyser.*;
import rho.runtime.Env;
import rho.types.ValueType.TypeVar;

import static rho.types.ValueType.SetType.setType;
import static rho.types.ValueType.SimpleType.*;
import static rho.types.ValueType.VectorType.vectorType;

public class TypeChecker {

    private static ValueType typeValueExpr0(Env env, LocalTypeEnv localTypeEnv, ValueExpr expr) {
        return expr.accept(new ValueExprVisitor<ValueType>() {
            @Override
            public ValueType visit(ValueExpr.BoolExpr expr) {
                return BOOL_TYPE;
            }

            @Override
            public ValueType visit(ValueExpr.StringExpr expr) {
                return STRING_TYPE;
            }

            @Override
            public ValueType visit(ValueExpr.IntExpr expr) {
                return INT_TYPE;
            }

            @Override
            public ValueType visit(ValueExpr.VectorExpr expr) {
                ValueType innerType = new TypeVar();
                TypeMapping mapping = TypeMapping.EMPTY;

                for (ValueExpr el : expr.exprs) {
                    ValueType elType = typeValueExpr0(env, localTypeEnv, el);
                    mapping = mapping.with(innerType.unify(elType));
                    innerType = innerType.apply(mapping);
                }

                return vectorType(innerType);
            }

            @Override
            public ValueType visit(ValueExpr.SetExpr expr) {
                ValueType innerType = new TypeVar();
                TypeMapping mapping = TypeMapping.EMPTY;

                for (ValueExpr el : expr.exprs) {
                    ValueType elType = typeValueExpr0(env, localTypeEnv, el);
                    mapping = mapping.with(innerType.unify(elType));
                    innerType = innerType.apply(mapping);
                }

                return setType(innerType);
            }

            @Override
            public ValueType visit(ValueExpr.CallExpr expr) {
                PVector<ValueExpr> params = expr.params;
                ValueType firstParamType = typeValueExpr0(env, localTypeEnv, params.get(0));

                if (firstParamType instanceof FnType) {
                    FnType fnType = (FnType) firstParamType;

                    PVector<ValueType> fnParamTypes = fnType.paramTypes;
                    if (fnParamTypes.size() == params.size() - 1) {
                        TypeMapping mapping = TypeMapping.EMPTY;
                        for (int i = 0; i < fnParamTypes.size(); i++) {
                            ValueType paramType = fnParamTypes.get(i).apply(mapping);
                            ValueType exprType = typeValueExpr0(env, localTypeEnv, params.get(i + 1)).apply(mapping);

                            mapping = mapping.with(paramType.unify(exprType));
                        }

                        return fnType.returnType.apply(mapping);
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueType visit(ValueExpr.VarCallExpr expr) {
                ValueType varType = expr.var.type.instantiate();

                if (varType instanceof ValueType.FnType) {
                    FnType fnType = (FnType) varType;
                    TypeMapping mapping = TypeMapping.EMPTY;

                    if (expr.params.size() != fnType.paramTypes.size()) {
                        throw new UnsupportedOperationException();
                    }

                    for (int i = 0; i < expr.params.size(); i++) {
                        ValueType paramType = fnType.paramTypes.get(i).apply(mapping);
                        ValueType exprType = typeValueExpr0(env, localTypeEnv, expr.params.get(i)).apply(mapping);

                        mapping = mapping.with(paramType.unify(exprType));
                    }

                    return fnType.returnType.apply(mapping);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueType visit(ValueExpr.LetExpr expr) {
                LocalTypeEnv localTypeEnv_ = localTypeEnv;

                for (ValueExpr.LetExpr.LetBinding letBinding : expr.bindings) {
                    ValueType bindingType = typeValueExpr0(env, localTypeEnv_, letBinding.expr);
                    localTypeEnv_ = localTypeEnv_.with(letBinding.localVar, bindingType);
                }

                return typeValueExpr0(env, localTypeEnv_, expr.body);
            }

            @Override
            public ValueType visit(ValueExpr.IfExpr expr) {
                TypeMapping mapping = typeValueExpr0(env, localTypeEnv, expr.testExpr).unify(BOOL_TYPE);
                TypeVar returnType = new TypeVar();
                mapping = typeValueExpr0(env, localTypeEnv, expr.thenExpr).apply(mapping).unify(returnType);
                mapping = typeValueExpr0(env, localTypeEnv, expr.elseExpr).apply(mapping).unify(returnType);

                return returnType.apply(mapping);
            }

            @Override
            public ValueType visit(ValueExpr.LocalVarExpr expr) {
                ValueType type = localTypeEnv.env.get(expr.localVar);
                if (type != null) {
                    return type;
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueType visit(ValueExpr.GlobalVarExpr expr) {
                return expr.var.type.instantiate();
            }
        });
    }

    public static ValueType typeValueExpr(Env env, ValueExpr expr) {
        return typeValueExpr0(env, LocalTypeEnv.EMPTY_TYPE_ENV, expr);
    }

    public static ActionType typeActionExpr(Env env, ActionExpr expr) {
        return expr.accept(new ActionExprVisitor<ActionType>() {
            @Override
            public ActionType visit(ActionExpr.DefExpr expr) {
                return new ActionType.DefType(typeValueExpr(env, expr.body));
            }
        });
    }

    public static Type type(Env env, Expr expr) {
        return expr.accept(new ExprVisitor<Type>() {
            @Override
            public Type accept(ValueExpr expr) {
                return typeValueExpr(env, expr);
            }

            @Override
            public Type accept(ActionExpr expr) {
                return typeActionExpr(env, expr);
            }
        });
    }

}