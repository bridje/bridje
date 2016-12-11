package bridje.types;

import bridje.analyser.Expr;

class TypeResult {
    final MonomorphicEnv monomorphicEnv;
    final Expr<Type> expr;

    public TypeResult(Expr<Type> typedExpr) {
        this.monomorphicEnv = MonomorphicEnv.EMPTY;
        this.expr = typedExpr;
    }

    TypeResult(MonomorphicEnv monomorphicEnv, Expr<Type> typedExpr) {
        this.monomorphicEnv = monomorphicEnv;
        this.expr = typedExpr;
    }
}
