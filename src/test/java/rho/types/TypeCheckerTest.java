package rho.types;

import org.junit.Test;
import rho.Panic;
import rho.analyser.LocalVar;
import rho.analyser.ValueExpr;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.ExprUtil.*;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_VAR;
import static rho.types.ActionType.DefType.defType;
import static rho.types.ValueType.FnType.fnType;
import static rho.types.ValueType.SetType.setType;
import static rho.types.ValueType.SimpleType.INT_TYPE;
import static rho.types.ValueType.SimpleType.STRING_TYPE;
import static rho.types.ValueType.VectorType.vectorType;

public class TypeCheckerTest {

    @Test
    public void typesVector() throws Exception {
        assertEquals(vectorType(STRING_TYPE), TypeChecker.type(vectorExpr(null, vectorOf(stringExpr(STRING_TYPE, "Hello"), stringExpr(STRING_TYPE, "World")))));
    }

    @Test(expected = Panic.class)
    public void failsMixedVector() throws Exception {
        TypeChecker.type(vectorExpr(null, vectorOf(stringExpr(STRING_TYPE, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesSet() throws Exception {
        assertEquals(setType(INT_TYPE), TypeChecker.type(setExpr(null, vectorOf(intExpr(null, 16), intExpr(null, 9)))));
    }

    @Test(expected = Panic.class)
    public void failsMixedSet() throws Exception {
        TypeChecker.type(setExpr(null, vectorOf(stringExpr(STRING_TYPE, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesPlusCall() throws Exception {
        assertEquals(INT_TYPE, TypeChecker.type(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))));
    }

    @Test
    public void typesPlusValue() throws Exception {
        assertEquals(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), TypeChecker.type(globalVarExpr(null, PLUS_VAR)));
    }

    @Test
    public void typesIfExpr() throws Exception {
        assertEquals(STRING_TYPE, TypeChecker.type(ifExpr(null, boolExpr(null, false), stringExpr(STRING_TYPE, "is true"), stringExpr(STRING_TYPE, "is false"))));
    }

    @Test
    public void typesLet() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));

        ValueExpr.LetExpr<?> letExpr = letExpr(null,
            vectorOf(
                letBinding(x, intExpr(null, 4)),
                letBinding(y, intExpr(null, 3))),
            vectorExpr(null, vectorOf(localVarExpr(null, x), localVarExpr(null, y))));

        assertEquals(vectorType(INT_TYPE), TypeChecker.type(letExpr));
    }

    @Test
    public void typesCallExpr() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        assertEquals(INT_TYPE, TypeChecker.type(
            letExpr(null,
                vectorOf(
                    letBinding(x, globalVarExpr(null, PLUS_VAR))),
                callExpr(null, vectorOf(localVarExpr(null, x), intExpr(null, 1), intExpr(null, 2))))));
    }

    @Test
    public void typesDefValue() throws Exception {
        assertEquals(defType(INT_TYPE), TypeChecker.type(defExpr(symbol("x"), varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2))))));
    }
}