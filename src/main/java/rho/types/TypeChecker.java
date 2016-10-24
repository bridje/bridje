package rho.types;

import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.analyser.Expr;
import rho.analyser.ExprVisitor;
import rho.analyser.ValueExpr;
import rho.analyser.ValueExprVisitor;
import rho.runtime.Env;
import rho.types.Type.TypeVar;

import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.types.Type.SimpleType.STRING_TYPE;
import static rho.types.Type.VectorType.vectorType;

public class TypeChecker {

    private static Type type0(Env env, Expr expr) {
        return expr.accept(new ExprVisitor<Type>() {
            @Override
            public Type accept(ValueExpr expr) {
                return expr.accept(new ValueExprVisitor<Type>() {
                    @Override
                    public Type accept(ValueExpr.StringExpr expr) {
                        return STRING_TYPE;
                    }

                    @Override
                    public Type accept(ValueExpr.IntExpr expr) {
                        return INT_TYPE;
                    }

                    @Override
                    public Type accept(ValueExpr.VectorExpr expr) {
                        Type innerType = new TypeVar();
                        PMap<TypeVar, Type> mapping = Empty.map();

                        for (Expr el : expr.exprs) {
                            Type elType = type0(env, el);
                            mapping = mapping.plusAll(innerType.unify(elType));
                            innerType = innerType.apply(mapping);
                        }

                        return vectorType(innerType);
                    }

                    @Override
                    public Type accept(ValueExpr.SetExpr expr) {
                        Type innerType = new TypeVar();
                        PMap<TypeVar, Type> mapping = Empty.map();

                        for (Expr el : expr.exprs) {
                            Type elType = type0(env, el);
                            mapping = mapping.plusAll(innerType.unify(elType));
                            innerType = innerType.apply(mapping);
                        }

                        return setType(innerType);
                    }
                });
            }
        });
    }

    public static Type type(Env env, Expr expr) {
        return type0(env, expr);
    }

}
