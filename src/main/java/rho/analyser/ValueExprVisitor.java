package rho.analyser;

public interface ValueExprVisitor<T> {
    T visit(ValueExpr.BoolExpr expr);

    T visit(ValueExpr.StringExpr expr);

    T visit(ValueExpr.IntExpr expr);

    T visit(ValueExpr.VectorExpr expr);

    T visit(ValueExpr.SetExpr expr);

    T visit(ValueExpr.CallExpr expr);
}
