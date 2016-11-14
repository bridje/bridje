package rho.types;

import org.junit.Test;
import rho.Panic;
import rho.analyser.ActionExpr;
import rho.analyser.LocalVar;
import rho.analyser.ValueExpr;
import rho.reader.Form;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.ExprUtil.*;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_VAR;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.types.Type.SimpleType.STRING_TYPE;
import static rho.types.Type.VectorType.vectorType;

public class TypeCheckerTest {

    @Test
    public void typesVector() throws Exception {
        assertEquals(vectorType(STRING_TYPE), TypeChecker.typeValueExpr(vectorExpr(null, vectorOf(stringExpr(null, "Hello"), stringExpr(null, "World")))).data.type);
    }

    @Test(expected = Panic.class)
    public void failsMixedVector() throws Exception {
        TypeChecker.type(vectorExpr(null, vectorOf(stringExpr(null, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesSet() throws Exception {
        assertEquals(setType(INT_TYPE), TypeChecker.typeValueExpr(setExpr(null, vectorOf(intExpr(null, 16), intExpr(null, 9)))).data.type);
    }

    @Test(expected = Panic.class)
    public void failsMixedSet() throws Exception {
        TypeChecker.type(setExpr(null, vectorOf(stringExpr(null, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesPlusCall() throws Exception {
        assertEquals(INT_TYPE, TypeChecker.typeValueExpr(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))).data.type);
    }

    @Test
    public void typesPlusValue() throws Exception {
        assertEquals(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), TypeChecker.typeValueExpr(globalVarExpr(null, PLUS_VAR)).data.type);
    }

    @Test
    public void typesIfExpr() throws Exception {
        assertEquals(STRING_TYPE, TypeChecker.typeValueExpr(ifExpr(null, boolExpr(null, false), stringExpr(null, "is true"), stringExpr(null, "is false"))).data.type);
    }

    @Test
    public void typesLet() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));

        ValueExpr.LetExpr<Form> letExpr = letExpr(null,
            vectorOf(
                letBinding(x, intExpr(null, 4)),
                letBinding(y, intExpr(null, 3))),
            vectorExpr(null, vectorOf(localVarExpr(null, x), localVarExpr(null, y))));

        assertEquals(vectorType(INT_TYPE), TypeChecker.typeValueExpr(letExpr).data.type);
    }

    @Test
    public void typesCallExpr() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        ValueExpr<TypedExprData> typedLetExpr = TypeChecker.typeValueExpr(
            letExpr(null,
                vectorOf(
                    letBinding(x, globalVarExpr(null, PLUS_VAR))),
                callExpr(null, vectorOf(localVarExpr(null, x), intExpr(null, 1), intExpr(null, 2)))));

        assertEquals(INT_TYPE, typedLetExpr.data.type);

        ValueExpr<TypedExprData> typedBodyExpr = ((ValueExpr.LetExpr<TypedExprData>) typedLetExpr).body;
        ValueExpr.CallExpr<TypedExprData> typedCallExpr = (ValueExpr.CallExpr<TypedExprData>) typedBodyExpr;

        assertEquals(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), typedCallExpr.exprs.get(0).data.type);
    }

    @Test
    public void typesDef() throws Exception {
        assertEquals(INT_TYPE, ((ActionExpr.DefExpr<TypedExprData>) TypeChecker.type(defExpr(symbol("x"), varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))))).body.data.type);
    }

    @Test
    public void typesInlineFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));

        assertEquals(
            fnType(vectorOf(INT_TYPE), INT_TYPE),
            TypeChecker.typeValueExpr(
                fnExpr(null,
                    vectorOf(x),
                    callExpr(null,
                        vectorOf(
                            globalVarExpr(null, PLUS_VAR),
                            localVarExpr(null, x),
                            localVarExpr(null, x))))).data.type);
    }
}