package rho.analyser;

public interface ActionExprVisitor<VED, T> {

    T visit(ActionExpr.DefExpr<? extends VED> expr);
}
