package rho.analyser;

public interface ActionExprVisitor<T> {

    T visit(ActionExpr.DefExpr expr);
}
