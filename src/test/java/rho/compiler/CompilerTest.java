package rho.compiler;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.analyser.LocalVar;
import rho.analyser.ValueExpr;
import rho.runtime.Env;
import rho.runtime.EvalResult;

import static org.junit.Assert.assertEquals;
import static rho.Util.setOf;
import static rho.Util.vectorOf;
import static rho.analyser.LocalVar.localVar;
import static rho.analyser.ValueExpr.BoolExpr.boolExpr;
import static rho.analyser.ValueExpr.CallExpr.callExpr;
import static rho.analyser.ValueExpr.IfExpr.ifExpr;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.LetExpr.LetBinding.letBinding;
import static rho.analyser.ValueExpr.LetExpr.letExpr;
import static rho.analyser.ValueExpr.LocalVarExpr.localVarExpr;
import static rho.analyser.ValueExpr.SetExpr.setExpr;
import static rho.analyser.ValueExpr.StringExpr.stringExpr;
import static rho.analyser.ValueExpr.VectorExpr.vectorExpr;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_VAR;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.*;
import static rho.types.Type.VectorType.vectorType;

public class CompilerTest {

    @Test
    public void compilesBoolean() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), boolExpr(false));
        assertEquals(BOOL_TYPE, evalResult.type);
        assertEquals(false, evalResult.value);
    }

    @Test
    public void compilesString() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), stringExpr("hello world!"));
        assertEquals(STRING_TYPE, evalResult.type);
        assertEquals("hello world!", evalResult.value);
    }

    @Test
    public void compilesInt() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), intExpr(513));
        assertEquals(INT_TYPE, evalResult.type);
        assertEquals(513L, evalResult.value);
    }

    @Test
    public void compilesVector() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), vectorExpr(vectorOf(stringExpr("Hello"), stringExpr("World!"))));
        assertEquals(vectorType(STRING_TYPE), evalResult.type);
        assertEquals(vectorOf("Hello", "World!"), evalResult.value);
    }

    @Test
    public void compilesSet() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), setExpr(vectorOf(stringExpr("Hello"), stringExpr("World!"))));
        assertEquals(setType(STRING_TYPE), evalResult.type);
        assertEquals(setOf("Hello", "World!"), evalResult.value);
    }

    @Test
    public void compilesPlus() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        EvalResult evalResult = Compiler.evalValue(env, callExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2))));
        assertEquals(INT_TYPE, evalResult.type);
        assertEquals(3L, evalResult.value);
    }

    @Test
    public void compilesIf() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), ifExpr(boolExpr(false), stringExpr("is true"), stringExpr("is false")));
        assertEquals(STRING_TYPE, evalResult.type);
        assertEquals("is false", evalResult.value);
    }

    @Test
    public void compilesLet() throws Exception {
        LocalVar x = localVar(symbol("x"));
        LocalVar y = localVar(symbol("y"));

        ValueExpr.LetExpr letExpr = letExpr(
            vectorOf(
                letBinding(x, intExpr(4)),
                letBinding(y, intExpr(3))),
            vectorExpr(vectorOf(localVarExpr(x), localVarExpr(y))));

        EvalResult evalResult = Compiler.evalValue(Env.env(), letExpr);
        assertEquals(vectorType(INT_TYPE), evalResult.type);
        assertEquals(vectorOf(4L, 3L), evalResult.value);
    }
}