package bridje.analyser;

import bridje.runtime.Symbol;
import bridje.runtime.Var;
import org.pcollections.PVector;

import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.USER;

public class ExprUtil {

    public static <ET> Expr.IntExpr<ET> intExpr(ET type, int value) {
        return new Expr.IntExpr<>(null, type, value);
    }

    public static <ET> Expr.BoolExpr<ET> boolExpr(ET type, boolean value) {
        return new Expr.BoolExpr<>(null, type, value);
    }

    public static <ET> Expr.StringExpr<ET> stringExpr(ET type, String string) {
        return new Expr.StringExpr<>(null, type, string);
    }

    public static <ET> Expr.VectorExpr<ET> vectorExpr(ET type, PVector<Expr<ET>> exprs) {
        return new Expr.VectorExpr<>(null, type, exprs);
    }

    public static <ET> Expr.SetExpr<ET> setExpr(ET type, PVector<Expr<ET>> exprs) {
        return new Expr.SetExpr<>(null, type, exprs);
    }

    public static <ET> Expr.CallExpr<ET> callExpr(ET type, PVector<Expr<ET>> exprs) {
        return new Expr.CallExpr<>(null, type, exprs);
    }

    public static <ET> Expr.IfExpr<ET> ifExpr(ET type, Expr<ET> testExpr, Expr<ET> thenExpr, Expr<ET> elseExpr) {
        return new Expr.IfExpr<>(null, type, testExpr, thenExpr, elseExpr);
    }

    public static <ET> Expr.LocalVarExpr<ET> localVarExpr(ET type, LocalVar localVar) {
        return new Expr.LocalVarExpr<>(null, type, localVar);
    }

    public static <ET> Expr.VarCallExpr<ET> varCallExpr(ET type, Var var, PVector<Expr<ET>> params) {
        return new Expr.VarCallExpr<>(null, type, var, params);
    }

    public static <ET> Expr.GlobalVarExpr<ET> globalVarExpr(ET type, Var var) {
        return new Expr.GlobalVarExpr<>(null, type, var);
    }

    public static <ET> Expr.LetExpr.LetBinding<ET> letBinding(LocalVar localVar, Expr<ET> expr) {
        return new Expr.LetExpr.LetBinding<>(localVar, expr);
    }

    public static <ET> Expr.LetExpr<ET> letExpr(ET type, PVector<Expr.LetExpr.LetBinding<ET>> bindings, Expr<ET> body) {
        return new Expr.LetExpr<>(null, type, bindings, body);
    }

    public static <ET> Expr.DefExpr<ET> defExpr(ET type, Symbol sym, Expr<ET> expr) {
        return new Expr.DefExpr<>(null, type, fqSym(USER, sym), expr);
    }

    public static <ET> Expr.FnExpr<ET> fnExpr(ET type, PVector<LocalVar> params, Expr<ET> body) {
        return new Expr.FnExpr<>(null, type, params, body);
    }
}
