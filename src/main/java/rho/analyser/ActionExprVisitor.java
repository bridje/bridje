package rho.analyser;

public interface ActionExprVisitor<VED, T> {

    T visit(ActionExpr.DefExpr<? extends VED> expr);

    T visit(ActionExpr.TypeDefExpr<? extends VED> expr);
}
