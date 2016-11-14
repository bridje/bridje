package rho;

import org.junit.Test;
import rho.analyser.ActionExpr;
import rho.analyser.ValueExpr;
import rho.reader.Form;
import rho.reader.LCReader;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.types.Type;
import rho.types.TypeChecker;
import rho.types.TypedExprData;
import rho.util.Pair;

import static org.junit.Assert.assertEquals;
import static rho.analyser.Analyser.analyse;
import static rho.compiler.Compiler.compile;
import static rho.reader.FormReader.read;
import static rho.runtime.VarUtil.PLUS_ENV;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.util.Pair.pair;

public class E2EEval {

    private void testEvalsValueExpr(Env env, String code, Type expectedType, Object expectedResult) {
        Form form = read(LCReader.fromString(code));

        ValueExpr<? extends Form> expr = (ValueExpr<? extends Form>) analyse(env, form);

        ValueExpr<TypedExprData> typedExpr = TypeChecker.typeValueExpr(expr);

        EvalResult result = compile(env, typedExpr);

        assertEquals(expectedType, typedExpr.data.type);
        assertEquals(env, result.env);
        assertEquals(expectedResult, result.value);
    }

    private Pair<ActionExpr<TypedExprData>, EvalResult> evalActionExpr(Env env, String code) {
        Form form = read(LCReader.fromString(code));

        ActionExpr<? extends Form> expr = (ActionExpr<? extends Form>) analyse(env, form);

        ActionExpr<TypedExprData> typedExpr = TypeChecker.typeActionExpr(expr);

        return pair(typedExpr, compile(env, typedExpr));
    }

    @Test
    public void evalsLet() throws Exception {
        testEvalsValueExpr(PLUS_ENV, "(let [x 4, y 3] (+ x (+ x y)))", INT_TYPE, 11L);
    }

    @Test
    public void evalsAnonymousFn() throws Exception {
        testEvalsValueExpr(PLUS_ENV, "(let [double (fn (x) (+ x x))] (double 4))", INT_TYPE, 8L);
    }

    @Test
    public void evalsFnWithClosedOverLocals() throws Exception {
        testEvalsValueExpr(PLUS_ENV, "(let [y 2, add-y (fn (x) (+ x y))] (add-y 4))", INT_TYPE, 6L);
    }

}
