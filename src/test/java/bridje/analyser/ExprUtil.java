package bridje.analyser;

import bridje.runtime.Symbol;
import bridje.runtime.Var;
import org.pcollections.PVector;

import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.USER;

public class ExprUtil {

    public static ValueExpr.IntExpr intExpr(int value) {
        return new ValueExpr.IntExpr(null, value);
    }

    public static ValueExpr.BoolExpr boolExpr(boolean value) {
        return new ValueExpr.BoolExpr(null, value);
    }

    public static ValueExpr.StringExpr stringExpr(String string) {
        return new ValueExpr.StringExpr(null, string);
    }

    public static ValueExpr.VectorExpr vectorExpr(PVector<ValueExpr> exprs) {
        return new ValueExpr.VectorExpr(null, exprs);
    }

    public static ValueExpr.SetExpr setExpr(PVector<ValueExpr> exprs) {
        return new ValueExpr.SetExpr(null, exprs);
    }

    public static ValueExpr.CallExpr callExpr(PVector<ValueExpr> exprs) {
        return new ValueExpr.CallExpr(null, exprs);
    }

    public static ValueExpr.IfExpr ifExpr(ValueExpr testExpr, ValueExpr thenExpr, ValueExpr elseExpr) {
        return new ValueExpr.IfExpr(null, testExpr, thenExpr, elseExpr);
    }

    public static ValueExpr.LocalVarExpr localVarExpr(LocalVar localVar) {
        return new ValueExpr.LocalVarExpr(null, localVar);
    }

    public static ValueExpr.VarCallExpr varCallExpr(Var var, PVector<ValueExpr> params) {
        return new ValueExpr.VarCallExpr(null, var, params);
    }

    public static ValueExpr.GlobalVarExpr globalVarExpr(Var var) {
        return new ValueExpr.GlobalVarExpr(null, var);
    }

    public static ValueExpr.LetExpr.LetBinding letBinding(LocalVar localVar, ValueExpr expr) {
        return new ValueExpr.LetExpr.LetBinding(localVar, expr);
    }

    public static ValueExpr.LetExpr letExpr(PVector<ValueExpr.LetExpr.LetBinding> bindings, ValueExpr body) {
        return new ValueExpr.LetExpr(null, bindings, body);
    }

    public static ActionExpr.DefExpr defExpr(Symbol sym, PVector<LocalVar> params, ValueExpr expr) {
        return new ActionExpr.DefExpr(null, fqSym(USER, sym), params, expr);
    }

    public static ValueExpr.FnExpr fnExpr(PVector<LocalVar> params, ValueExpr body) {
        return new ValueExpr.FnExpr(null, params, body);
    }
}
