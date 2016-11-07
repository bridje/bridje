package rho.analyser;

import org.pcollections.PVector;
import rho.runtime.Symbol;
import rho.runtime.Var;

public class ExprUtil {

    public static <VED> ValueExpr.IntExpr<VED> intExpr(VED data, int value) {
        return new ValueExpr.IntExpr<>(data, value);
    }

    public static <VED> ValueExpr.BoolExpr<VED> boolExpr(VED data, boolean value) {
        return new ValueExpr.BoolExpr<>(data, value);
    }

    public static <VED> ValueExpr.StringExpr<VED> stringExpr(VED data, String string) {
        return new ValueExpr.StringExpr<>(data, string);
    }

    public static <VED> ValueExpr.VectorExpr<VED> vectorExpr(VED data, PVector<ValueExpr<VED>> exprs) {
        return new ValueExpr.VectorExpr<>(data, exprs);
    }

    public static <VED> ValueExpr.SetExpr<VED> setExpr(VED data, PVector<ValueExpr<VED>> exprs) {
        return new ValueExpr.SetExpr<>(data, exprs);
    }

    public static <VED> ValueExpr.CallExpr<VED> callExpr(VED data, PVector<ValueExpr<VED>> exprs) {
        return new ValueExpr.CallExpr<>(data, exprs);
    }

    public static <VED> ValueExpr.IfExpr<VED> ifExpr(VED data, ValueExpr<VED> testExpr, ValueExpr<VED> thenExpr, ValueExpr<VED> elseExpr) {
        return new ValueExpr.IfExpr<>(data, testExpr, thenExpr, elseExpr);
    }

    public static <VED> ValueExpr.LocalVarExpr<VED> localVarExpr(VED data, LocalVar localVar) {
        return new ValueExpr.LocalVarExpr<>(data, localVar);
    }

    public static <VED> ValueExpr.VarCallExpr<VED> varCallExpr(VED data, Var var, PVector<ValueExpr<VED>> params) {
        return new ValueExpr.VarCallExpr<>(data, var, params);
    }

    public static <VED> ValueExpr.GlobalVarExpr<VED> globalVarExpr(VED data, Var var) {
        return new ValueExpr.GlobalVarExpr<>(data, var);
    }

    public static <VED> ValueExpr.LetExpr.LetBinding<VED> letBinding(LocalVar localVar, ValueExpr<VED> expr) {
        return new ValueExpr.LetExpr.LetBinding<>(localVar, expr);
    }

    public static <VED> ValueExpr.LetExpr<VED> letExpr(VED data, PVector<ValueExpr.LetExpr.LetBinding<VED>> bindings, ValueExpr<VED> body) {
        return new ValueExpr.LetExpr<>(data, bindings, body);
    }

    public static <VED> ActionExpr.DefExpr<VED> defExpr(Symbol sym, ValueExpr<VED> expr) {
        return new ActionExpr.DefExpr<>(sym, expr);
    }
}
