package rho;

import org.junit.Test;
import rho.analyser.ValueExpr;
import rho.reader.Form;
import rho.reader.LCReader;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.types.TypeChecker;
import rho.types.TypedExprData;

import static org.junit.Assert.assertEquals;
import static rho.analyser.Analyser.analyse;
import static rho.compiler.Compiler.compile;
import static rho.reader.FormReader.read;
import static rho.runtime.VarUtil.PLUS_ENV;
import static rho.types.Type.SimpleType.INT_TYPE;

public class E2EEval {

    @Test
    public void evalsLet() throws Exception {
        Env env = PLUS_ENV;

        Form form = read(LCReader.fromString("(let [x 4, y 3] (+ x (+ x y)))"));

        ValueExpr<? extends Form> expr = (ValueExpr<? extends Form>) analyse(env, form);

        ValueExpr<TypedExprData> typedExpr = TypeChecker.typeValueExpr(expr);

        EvalResult result = compile(env, typedExpr);

        assertEquals(INT_TYPE, typedExpr.data.type);
        assertEquals(env, result.env);
        assertEquals(11L, result.value);
    }
}
