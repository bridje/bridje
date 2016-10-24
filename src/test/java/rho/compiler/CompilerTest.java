package rho.compiler;

import org.junit.Test;
import rho.runtime.Env;
import rho.runtime.EvalResult;

import static org.junit.Assert.assertEquals;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.StringExpr.stringExpr;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.types.Type.SimpleType.STRING_TYPE;

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
}