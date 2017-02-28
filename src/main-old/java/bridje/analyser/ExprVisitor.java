package bridje.analyser;

public interface ExprVisitor<T> {

    T visit(Expr.BoolExpr expr);

    T visit(Expr.StringExpr expr);

    T visit(Expr.IntExpr expr);

    T visit(Expr.VectorExpr expr);

    T visit(Expr.SetExpr expr);

    T visit(Expr.CallExpr expr);

    T visit(Expr.VarCallExpr expr);

    T visit(Expr.LetExpr expr);

    T visit(Expr.IfExpr expr);

    T visit(Expr.LocalVarExpr expr);

    T visit(Expr.GlobalVarExpr expr);

    T visit(Expr.FnExpr expr);

    T visit(Expr.NSExpr expr);

    T visit(Expr.DefExpr expr);

    T visit(Expr.DefJExpr expr);

    T visit(Expr.DefDataExpr expr);
}
