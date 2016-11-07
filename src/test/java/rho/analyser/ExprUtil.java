package rho.analyser;

import org.pcollections.PVector;
import rho.runtime.Symbol;
import rho.runtime.Var;
import rho.types.ValueTypeHole;

public class ExprUtil {

    public static <VT extends ValueTypeHole> ValueExpr.IntExpr<VT> intExpr(VT type, int value) {
        return new ValueExpr.IntExpr<>(null, type, value);
    }

    public static <VT extends ValueTypeHole> ValueExpr.BoolExpr<VT> boolExpr(VT type, boolean value) {
        return new ValueExpr.BoolExpr<>(null, type, value);
    }

    public static <VT extends ValueTypeHole> ValueExpr.StringExpr<VT> stringExpr(VT type, String string) {
        return new ValueExpr.StringExpr<>(null, type, string);
    }

    public static <VT extends ValueTypeHole> ValueExpr.VectorExpr<VT> vectorExpr(VT type, PVector<ValueExpr<VT>> exprs) {
        return new ValueExpr.VectorExpr<>(null, type, exprs);
    }

    public static <VT extends ValueTypeHole> ValueExpr.SetExpr<VT> setExpr(VT type, PVector<ValueExpr<VT>> exprs) {
        return new ValueExpr.SetExpr<>(null, type, exprs);
    }

    public static <VT extends ValueTypeHole> ValueExpr.CallExpr<VT> callExpr(VT type, PVector<ValueExpr<VT>> exprs) {
        return new ValueExpr.CallExpr<>(null, type, exprs);
    }

    public static <VT extends ValueTypeHole> ValueExpr.IfExpr<VT> ifExpr(VT type, ValueExpr<VT> testExpr, ValueExpr<VT> thenExpr, ValueExpr<VT> elseExpr) {
        return new ValueExpr.IfExpr<>(null, type, testExpr, thenExpr, elseExpr);
    }

    public static <VT extends ValueTypeHole> ValueExpr.LocalVarExpr<VT> localVarExpr(VT type, LocalVar localVar) {
        return new ValueExpr.LocalVarExpr<>(null, type, localVar);
    }

    public static <VT extends ValueTypeHole> ValueExpr.VarCallExpr<VT> varCallExpr(VT type, Var var, PVector<ValueExpr<VT>> params) {
        return new ValueExpr.VarCallExpr<>(null, type, var, params);
    }

    public static <VT extends ValueTypeHole> ValueExpr.GlobalVarExpr<VT> globalVarExpr(VT type, Var var) {
        return new ValueExpr.GlobalVarExpr<>(null, type, var);
    }

    public static <VT extends ValueTypeHole> ValueExpr.LetExpr.LetBinding<VT> letBinding(LocalVar localVar, ValueExpr<VT> expr) {
        return new ValueExpr.LetExpr.LetBinding<>(null, localVar, expr);
    }

    public static <VT extends ValueTypeHole> ValueExpr.LetExpr<VT> letExpr(VT type, PVector<ValueExpr.LetExpr.LetBinding<VT>> bindings, ValueExpr<VT> body) {
        return new ValueExpr.LetExpr<>(null, type, bindings, body);
    }

    public static <VT extends ValueTypeHole> ActionExpr.DefExpr<VT> defExpr(Symbol sym, ValueExpr<VT> expr) {
        return new ActionExpr.DefExpr<>(null, sym, expr);
    }
}
