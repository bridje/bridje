package rho.analyser;

public interface ValueExprVisitor<D, T> {
    T visit(ValueExpr.BoolExpr<? extends D> expr);

    T visit(ValueExpr.StringExpr<? extends D> expr);

    T visit(ValueExpr.IntExpr<? extends D> expr);

    T visit(ValueExpr.VectorExpr<? extends D> expr);

    T visit(ValueExpr.SetExpr<? extends D> expr);

    T visit(ValueExpr.CallExpr<? extends D> expr);

    T visit(ValueExpr.VarCallExpr<? extends D> expr);

    T visit(ValueExpr.LetExpr<? extends D> expr);

    T visit(ValueExpr.IfExpr<? extends D> expr);

    T visit(ValueExpr.LocalVarExpr<? extends D> expr);

    T visit(ValueExpr.GlobalVarExpr<? extends D> expr);
}
