package rho.compiler;

import org.junit.Test;
import rho.runtime.Env;
import rho.runtime.EvalResult;

import static rho.analyser.ValueExpr.StringExpr.stringExpr;

public class CompilerTest {

    @Test
    public void compilesString() throws Exception {
        EvalResult evalResult = Compiler.evalValue(Env.env(), stringExpr("hello world!"));
        System.out.println(evalResult);
    }
}