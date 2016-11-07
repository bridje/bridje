package rho.analyser;

public abstract class Expr<VED> {

    Expr() {

    }

    public abstract <V> V accept(ExprVisitor<? super VED, V> visitor);
}
