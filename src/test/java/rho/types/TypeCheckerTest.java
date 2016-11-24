package rho.types;

import org.junit.Test;
import rho.Panic;
import rho.analyser.Expr;
import rho.analyser.LocalVar;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.ExprUtil.*;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_VAR;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.*;
import static rho.types.Type.VectorType.vectorType;

public class TypeCheckerTest {

    @Test
    public void typesVector() throws Exception {
        assertEquals(vectorType(STRING_TYPE), TypeChecker.typeExpr(vectorExpr(null, vectorOf(stringExpr(null, "Hello"), stringExpr(null, "World")))).type);
    }

    @Test(expected = Panic.class)
    public void failsMixedVector() throws Exception {
        TypeChecker.typeExpr(vectorExpr(null, vectorOf(stringExpr(null, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesSet() throws Exception {
        assertEquals(setType(INT_TYPE), TypeChecker.typeExpr(setExpr(null, vectorOf(intExpr(null, 16), intExpr(null, 9)))).type);
    }

    @Test(expected = Panic.class)
    public void failsMixedSet() throws Exception {
        TypeChecker.typeExpr(setExpr(null, vectorOf(stringExpr(null, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesPlusCall() throws Exception {
        assertEquals(INT_TYPE, TypeChecker.typeExpr(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))).type);
    }

    @Test
    public void typesPlusValue() throws Exception {
        assertEquals(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), TypeChecker.typeExpr(globalVarExpr(null, PLUS_VAR)).type);
    }

    @Test
    public void typesIfExpr() throws Exception {
        assertEquals(STRING_TYPE, TypeChecker.typeExpr(ifExpr(null, boolExpr(null, false), stringExpr(null, "is true"), stringExpr(null, "is false"))).type);
    }

    @Test
    public void typesLet() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));

        Expr.LetExpr<Void> letExpr = letExpr(null,
            vectorOf(
                letBinding(x, intExpr(null, 4)),
                letBinding(y, intExpr(null, 3))),
            vectorExpr(null, vectorOf(localVarExpr(null, x), localVarExpr(null, y))));

        assertEquals(vectorType(INT_TYPE), TypeChecker.typeExpr(letExpr).type);
    }

    @Test
    public void typesCallExpr() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        Expr<Type> typedLetExpr = TypeChecker.typeExpr(
            letExpr(null,
                vectorOf(
                    letBinding(x, globalVarExpr(null, PLUS_VAR))),
                callExpr(null, vectorOf(localVarExpr(null, x), intExpr(null, 1), intExpr(null, 2)))));

        assertEquals(INT_TYPE, typedLetExpr.type);

        Expr<Type> typedBodyExpr = ((Expr.LetExpr<Type>) typedLetExpr).body;
        Expr.CallExpr<Type> typedCallExpr = (Expr.CallExpr<Type>) typedBodyExpr;

        assertEquals(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), typedCallExpr.exprs.get(0).type);
    }

    @Test
    public void typesDef() throws Exception {
        assertEquals(INT_TYPE, ((Expr.DefExpr<Type>) TypeChecker.typeExpr(defExpr(null, symbol("x"), varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))))).body.type);
    }

    @Test
    public void typesInlineFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));

        assertEquals(
            fnType(vectorOf(INT_TYPE), INT_TYPE),
            TypeChecker.typeExpr(
                fnExpr(null,
                    vectorOf(x),
                    callExpr(null,
                        vectorOf(
                            globalVarExpr(null, PLUS_VAR),
                            localVarExpr(null, x),
                            localVarExpr(null, x))))).type);
    }

    @Test
    public void typesTypeDef() throws Exception {
        assertEquals(ENV_IO, TypeChecker.typeExpr(new Expr.TypeDefExpr<>(null, null, symbol("double"), fnType(vectorOf(INT_TYPE), INT_TYPE))).type);
    }
}