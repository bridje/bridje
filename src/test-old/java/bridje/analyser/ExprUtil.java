package bridje.analyser;

import bridje.runtime.Symbol;
import bridje.runtime.Var;
import org.pcollections.PVector;

import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.USER;

public class ExprUtil {

    public static Expr.IntExpr intExpr(int value) {
        return new Expr.IntExpr(null, value);
    }

    public static Expr.BoolExpr boolExpr(boolean value) {
        return new Expr.BoolExpr(null, value);
    }

    public static Expr.StringExpr stringExpr(String string) {
        return new Expr.StringExpr(null, string);
    }

    public static Expr.VectorExpr vectorExpr(PVector<Expr> exprs) {
        return new Expr.VectorExpr(null, exprs);
    }

    public static Expr.SetExpr setExpr(PVector<Expr> exprs) {
        return new Expr.SetExpr(null, exprs);
    }

    public static Expr.CallExpr callExpr(PVector<Expr> exprs) {
        return new Expr.CallExpr(null, exprs);
    }

    public static Expr.IfExpr ifExpr(Expr testExpr, Expr thenExpr, Expr elseExpr) {
        return new Expr.IfExpr(null, testExpr, thenExpr, elseExpr);
    }

    public static Expr.LocalVarExpr localVarExpr(LocalVar localVar) {
        return new Expr.LocalVarExpr(null, localVar);
    }

    public static Expr.VarCallExpr varCallExpr(Var var, PVector<Expr> params) {
        return new Expr.VarCallExpr(null, var, params);
    }

    public static Expr.GlobalVarExpr globalVarExpr(Var var) {
        return new Expr.GlobalVarExpr(null, var);
    }

    public static Expr.LetExpr.LetBinding letBinding(LocalVar localVar, Expr expr) {
        return new Expr.LetExpr.LetBinding(localVar, expr);
    }

    public static Expr.LetExpr letExpr(PVector<Expr.LetExpr.LetBinding> bindings, Expr body) {
        return new Expr.LetExpr(null, bindings, body);
    }

    public static Expr.DefExpr defExpr(Symbol sym, Expr expr) {
        return new Expr.DefExpr(null, fqSym(USER, sym), expr);
    }

    public static Expr.FnExpr fnExpr(PVector<LocalVar> params, Expr body) {
        return new Expr.FnExpr(null, params, body);
    }
}
