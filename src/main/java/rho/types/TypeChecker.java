package rho.types;

import rho.analyser.Expr;
import rho.analyser.ExprVisitor;
import rho.analyser.ValueExpr;
import rho.analyser.ValueExprVisitor;
import rho.runtime.Env;

public class TypeChecker {

    public static Type type(Env env, Expr expr) {
        return expr.accept(new ExprVisitor<Type>() {
            @Override
            public Type accept(ValueExpr expr) {
                return expr.accept(new ValueExprVisitor<Type>() {
                    @Override
                    public Type accept(ValueExpr.StringExpr expr) {
                        return Type.STRING_TYPE;
                    }
                });
            }
        });
    }

}
