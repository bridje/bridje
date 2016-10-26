package rho.types;

import rho.analyser.Expr;
import rho.analyser.ExprVisitor;
import rho.analyser.ValueExpr;
import rho.analyser.ValueExprVisitor;
import rho.runtime.Env;
import rho.types.Type.TypeVar;

import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.*;
import static rho.types.Type.VectorType.vectorType;

public class TypeChecker {

    private static Type type0(Env env, Expr expr) {
        return expr.accept(new ExprVisitor<Type>() {
            @Override
            public Type accept(ValueExpr expr) {
                return expr.accept(new ValueExprVisitor<Type>() {
                    @Override
                    public Type visit(ValueExpr.BoolExpr expr) {
                        return BOOL_TYPE;
                    }

                    @Override
                    public Type visit(ValueExpr.StringExpr expr) {
                        return STRING_TYPE;
                    }

                    @Override
                    public Type visit(ValueExpr.IntExpr expr) {
                        return INT_TYPE;
                    }

                    @Override
                    public Type visit(ValueExpr.VectorExpr expr) {
                        Type innerType = new TypeVar();
                        TypeMapping mapping = TypeMapping.EMPTY;

                        for (Expr el : expr.exprs) {
                            Type elType = type0(env, el);
                            mapping = mapping.with(innerType.unify(elType));
                            innerType = innerType.apply(mapping);
                        }

                        return vectorType(innerType);
                    }

                    @Override
                    public Type visit(ValueExpr.SetExpr expr) {
                        Type innerType = new TypeVar();
                        TypeMapping mapping = TypeMapping.EMPTY;

                        for (Expr el : expr.exprs) {
                            Type elType = type0(env, el);
                            mapping = mapping.with(innerType.unify(elType));
                            innerType = innerType.apply(mapping);
                        }

                        return setType(innerType);
                    }

                    @Override
                    public Type visit(ValueExpr.CallExpr expr) {
                        Type varType = expr.var.type.instantiate();

                        if (varType instanceof Type.FnType) {
                            FnType fnType = (FnType) varType;
                            TypeMapping mapping = TypeMapping.EMPTY;

                            if (expr.params.size() != fnType.paramTypes.size()) {
                                throw new UnsupportedOperationException();
                            }

                            for (int i = 0; i < expr.params.size(); i++) {
                                Type paramType = fnType.paramTypes.get(i).apply(mapping);
                                Type exprType = type0(env, expr.params.get(i)).apply(mapping);

                                mapping = mapping.with(paramType.unify(exprType));
                            }

                            return fnType.returnType.apply(mapping);
                        }

                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Type visit(ValueExpr.LetExpr expr) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Type visit(ValueExpr.IfExpr expr) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Type visit(ValueExpr.LocalVarExpr expr) {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        });
    }

    public static Type type(Env env, Expr expr) {
        return type0(env, expr);
    }

}
