package rho.compiler;

import org.junit.Test;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.types.Type;

import static org.junit.Assert.assertEquals;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.StringExpr.stringExpr;

public class CompilerTest {

    @Test
    public void compilesString() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), stringExpr("hello world!"));
        assertEquals(Type.STRING_TYPE, evalResult.type);
        assertEquals("hello world!", evalResult.value);
    }

    @Test
    public void compilesInt() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), intExpr(513));
        assertEquals(Type.INT_TYPE, evalResult.type);
        assertEquals(513L, evalResult.value);
    }
}