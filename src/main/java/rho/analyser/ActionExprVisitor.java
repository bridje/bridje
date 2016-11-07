package rho.analyser;

public interface ActionExprVisitor<T, VED> {

    T visit(ActionExpr.DefExpr<? extends VED> expr);
}
