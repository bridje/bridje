package bridje.analyser;

public interface ExprVisitor<ET, T> {

    T visit(Expr.BoolExpr<? extends ET> expr);

    T visit(Expr.StringExpr<? extends ET> expr);

    T visit(Expr.IntExpr<? extends ET> expr);

    T visit(Expr.VectorExpr<? extends ET> expr);

    T visit(Expr.SetExpr<? extends ET> expr);

    T visit(Expr.MapExpr<? extends ET> expr);

    T visit(Expr.CallExpr<? extends ET> expr);

    T visit(Expr.VarCallExpr<? extends ET> expr);

    T visit(Expr.LetExpr<? extends ET> expr);

    T visit(Expr.IfExpr<? extends ET> expr);

    T visit(Expr.LocalVarExpr<? extends ET> expr);

    T visit(Expr.GlobalVarExpr<? extends ET> expr);

    T visit(Expr.FnExpr<? extends ET> expr);

    T visit(Expr.NSExpr<? extends ET> expr);

    T visit(Expr.DefExpr<? extends ET> expr);

    T visit(Expr.TypeDefExpr<? extends ET> expr);

    T visit(Expr.DefJExpr<? extends ET> expr);

    T visit(Expr.DefDataExpr<? extends ET> expr);
}
