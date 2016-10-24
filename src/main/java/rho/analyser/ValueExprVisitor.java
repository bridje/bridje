package rho.analyser;

public interface ValueExprVisitor<T> {
    T accept(ValueExpr.StringExpr expr);

    T accept(ValueExpr.IntExpr expr);

    T accept(ValueExpr.VectorExpr expr);

    T accept(ValueExpr.SetExpr expr);
}
