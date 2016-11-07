package rho.analyser;

import rho.types.ValueTypeHole;

public interface ActionExprVisitor<T, VT extends ValueTypeHole> {

    T visit(ActionExpr.DefExpr<VT> expr);
}
