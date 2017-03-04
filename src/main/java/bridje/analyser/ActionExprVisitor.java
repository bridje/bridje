package bridje.analyser;

public interface ActionExprVisitor<T> {
    T visit(ActionExpr.DefExpr expr);

    T visit(ActionExpr.DefDataExpr expr);
}
