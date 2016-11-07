package rho.analyser;

public interface ExprVisitor<V, VED> {

    V accept(ValueExpr<? extends VED> expr);

    V accept(ActionExpr<? extends VED> expr);
}
