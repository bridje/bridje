package rho.analyser;

import rho.types.ValueTypeHole;

public interface ValueExprVisitor<T, VT extends ValueTypeHole> {
    T visit(ValueExpr.BoolExpr<VT> expr);

    T visit(ValueExpr.StringExpr<VT> expr);

    T visit(ValueExpr.IntExpr<VT> expr);

    T visit(ValueExpr.VectorExpr<VT> expr);

    T visit(ValueExpr.SetExpr<VT> expr);

    T visit(ValueExpr.CallExpr<VT> expr);

    T visit(ValueExpr.VarCallExpr<VT> expr);

    T visit(ValueExpr.LetExpr<VT> expr);

    T visit(ValueExpr.IfExpr<VT> expr);

    T visit(ValueExpr.LocalVarExpr<VT> expr);

    T visit(ValueExpr.GlobalVarExpr<VT> expr);
}
