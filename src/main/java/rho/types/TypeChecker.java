package rho.types;

import org.pcollections.PVector;
import rho.analyser.*;

import static rho.types.ValueType.SetType.setType;
import static rho.types.ValueType.SimpleType.*;
import static rho.types.ValueType.VectorType.vectorType;

public class TypeChecker {

    private static ValueType typeValueExpr0(LocalTypeEnv localTypeEnv, ValueExpr<?> expr) {
        return expr.accept(new ValueExprVisitor<ValueType, Object>() {
            @Override
            public ValueType visit(ValueExpr.BoolExpr<?> expr) {
                return BOOL_TYPE;
            }

            @Override
            public ValueType visit(ValueExpr.StringExpr<?> expr) {
                return STRING_TYPE;
            }

            @Override
            public ValueType visit(ValueExpr.IntExpr<?> expr) {
                return INT_TYPE;
            }

            @Override
            public ValueType visit(ValueExpr.VectorExpr<?> expr) {
                ValueType innerType = new TypeVar();
                TypeMapping mapping = TypeMapping.EMPTY;

                for (ValueExpr<?> el : expr.exprs) {
                    ValueType elType = typeValueExpr0(localTypeEnv, el);
                    mapping = mapping.with(innerType.unify(elType));
                    innerType = innerType.apply(mapping);
                }

                return vectorType(innerType);
            }

            @Override
            public ValueType visit(ValueExpr.SetExpr<?> expr) {
                ValueType innerType = new TypeVar();
                TypeMapping mapping = TypeMapping.EMPTY;

                for (ValueExpr<?> el : expr.exprs) {
                    ValueType elType = typeValueExpr0(localTypeEnv, el);
                    mapping = mapping.with(innerType.unify(elType));
                    innerType = innerType.apply(mapping);
                }

                return setType(innerType);
            }

            @Override
            public ValueType visit(ValueExpr.CallExpr<?> expr) {
                PVector<? extends ValueExpr<?>> params = expr.params;
                ValueType firstParamType = typeValueExpr0(localTypeEnv, params.get(0));

                if (firstParamType instanceof FnType) {
                    FnType fnType = (FnType) firstParamType;

                    PVector<ValueType> fnParamTypes = fnType.paramTypes;
                    if (fnParamTypes.size() == params.size() - 1) {
                        TypeMapping mapping = TypeMapping.EMPTY;
                        for (int i = 0; i < fnParamTypes.size(); i++) {
                            ValueType paramType = fnParamTypes.get(i).apply(mapping);
                            ValueType exprType = typeValueExpr0(localTypeEnv, params.get(i + 1)).apply(mapping);

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
            public ValueType visit(ValueExpr.VarCallExpr<?> expr) {
                ValueType varType = expr.var.type.instantiate();

                if (varType instanceof ValueType.FnType) {
                    FnType fnType = (FnType) varType;
                    TypeMapping mapping = TypeMapping.EMPTY;

                    if (expr.params.size() != fnType.paramTypes.size()) {
                        throw new UnsupportedOperationException();
                    }

                    for (int i = 0; i < expr.params.size(); i++) {
                        ValueType paramType = fnType.paramTypes.get(i).apply(mapping);
                        ValueType exprType = typeValueExpr0(localTypeEnv, expr.params.get(i)).apply(mapping);

                        mapping = mapping.with(paramType.unify(exprType));
                    }

                    return fnType.returnType.apply(mapping);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueType visit(ValueExpr.LetExpr<?> expr) {
                LocalTypeEnv localTypeEnv_ = localTypeEnv;

                for (ValueExpr.LetExpr.LetBinding<?> letBinding : expr.bindings) {
                    ValueType bindingType = typeValueExpr0(localTypeEnv_, letBinding.expr);
                    localTypeEnv_ = localTypeEnv_.with(letBinding.localVar, bindingType);
                }

                return typeValueExpr0(localTypeEnv_, expr.body);
            }

            @Override
            public ValueType visit(ValueExpr.IfExpr<?> expr) {
                TypeMapping mapping = typeValueExpr0(localTypeEnv, expr.testExpr).unify(BOOL_TYPE);
                TypeVar returnType = new TypeVar();
                mapping = typeValueExpr0(localTypeEnv, expr.thenExpr).apply(mapping).unify(returnType);
                mapping = typeValueExpr0(localTypeEnv, expr.elseExpr).apply(mapping).unify(returnType);

                return returnType.apply(mapping);
            }

            @Override
            public ValueType visit(ValueExpr.LocalVarExpr<?> expr) {
                ValueType type = localTypeEnv.env.get(expr.localVar);
                if (type != null) {
                    return type;
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueType visit(ValueExpr.GlobalVarExpr<?> expr) {
                return expr.var.type.instantiate();
            }
        });
    }

    public static ValueType typeValueExpr(ValueExpr<?> expr) {
        return typeValueExpr0(LocalTypeEnv.EMPTY_TYPE_ENV, expr);
    }

    public static ActionType typeActionExpr(ActionExpr<?> expr) {
        return expr.accept(new ActionExprVisitor<ActionType, Object>() {
            @Override
            public ActionType visit(ActionExpr.DefExpr<?> expr) {
                return new ActionType.DefType(typeValueExpr(expr.body));
            }
        });
    }

    public static Type type(Expr<?> expr) {
        return expr.accept(new ExprVisitor<Type, Object>() {
            @Override
            public Type accept(ValueExpr<?> expr) {
                return typeValueExpr(expr);
            }

            @Override
            public Type accept(ActionExpr<?> expr) {
                return typeActionExpr(expr);
            }
        });
    }

}