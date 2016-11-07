package rho.analyser;

import rho.types.ValueTypeHole;

public interface ExprVisitor<V, VT extends ValueTypeHole> {

    V accept(ValueExpr<VT> expr);

    V accept(ActionExpr<VT> expr);
}
