package rho.analyser;

public interface ExprVisitor<VED, T> {

    T accept(ValueExpr<? extends VED> expr);

    T accept(ActionExpr<? extends VED> expr);
}
