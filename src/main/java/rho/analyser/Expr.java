package rho.analyser;

public abstract class Expr<VED> {

    Expr() {

    }

    public abstract <V> V accept(ExprVisitor<V, ? super VED> visitor);
}
