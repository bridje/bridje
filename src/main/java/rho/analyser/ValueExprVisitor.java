package rho.analyser;

public interface ValueExprVisitor<T> {
    T accept(ValueExpr.StringExpr expr);
}
