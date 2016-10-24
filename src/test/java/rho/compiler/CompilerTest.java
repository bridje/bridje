package rho.compiler;

import org.junit.Test;
import rho.runtime.Env;
import rho.runtime.EvalResult;

import static org.junit.Assert.assertEquals;
import static rho.Util.setOf;
import static rho.Util.vectorOf;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.SetExpr.setExpr;
import static rho.analyser.ValueExpr.StringExpr.stringExpr;
import static rho.analyser.ValueExpr.VectorExpr.vectorExpr;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.types.Type.SimpleType.STRING_TYPE;
import static rho.types.Type.VectorType.vectorType;

public class CompilerTest {

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
}