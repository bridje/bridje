package rho;

import org.junit.Test;
import rho.analyser.Expr;
import rho.reader.Form;
import rho.reader.LCReader;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.runtime.Var;
import rho.types.Type;
import rho.types.TypeChecker;
import rho.util.Pair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static rho.Util.vectorOf;
import static rho.analyser.Analyser.analyse;
import static rho.compiler.Compiler.compile;
import static rho.reader.FormReader.read;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_ENV;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.util.Pair.pair;

public class E2EEval {

    private void testEvalsValue(Env env, String code, Type expectedType, Object expectedResult) {
        Form form = read(LCReader.fromString(code));

        Expr<Void> expr = analyse(env, form);

        Expr<Type> typedExpr = TypeChecker.typeExpr(expr);

        EvalResult result = compile(env, typedExpr);

        assertEquals(expectedType, typedExpr.type);
        assertEquals(env, result.env);
        assertEquals(expectedResult, result.value);
    }

    private Pair<Expr<Type>, EvalResult> testEvalsAction(Env env, String code) {
        Form form = read(LCReader.fromString(code));

        Expr<Void> expr = analyse(env, form);

        Expr<Type> typedExpr = TypeChecker.typeExpr(expr);

        return pair(typedExpr, compile(env, typedExpr));
    }

    @Test
    public void evalsLet() throws Exception {
        testEvalsValue(PLUS_ENV, "(let [x 4, y 3] (+ x (+ x y)))", INT_TYPE, 11L);
    }

    @Test
    public void evalsAnonymousFn() throws Exception {
        testEvalsValue(PLUS_ENV, "(let [double (fn (x) (+ x x))] (double 4))", INT_TYPE, 8L);
    }

    @Test
    public void evalsFnWithClosedOverLocals() throws Exception {
        testEvalsValue(PLUS_ENV, "(let [y 2, add-y (fn (x) (+ x y))] (add-y 4))", INT_TYPE, 6L);
    }

    @Test
    public void testsDefFn() throws Exception {
        Pair<Expr<Type>, EvalResult> result = testEvalsAction(PLUS_ENV, "(def (double x) (+ x x))");
        assertEquals(fnType(vectorOf(INT_TYPE), INT_TYPE),
            ((Expr.DefExpr<Type>) result.left).body.type);

        Env resultEnv = result.right.env;

        testEvalsValue(resultEnv, "(double 12)", INT_TYPE, 24L);
    }

    @Test
    public void testDefLetFn() throws Exception {
        // we can't (currently) compile straight to a method here - would be a static analysis optimisation
        // but at least we can avoid bugging out...
        Pair<Expr<Type>, EvalResult> result = testEvalsAction(PLUS_ENV, ("(def add-two (let [y 2] (fn (x) (+ x y))))"));
        assertEquals(fnType(vectorOf(INT_TYPE), INT_TYPE), ((Expr.DefExpr<Type>) result.left).body.type);

        testEvalsValue(result.right.env, "(add-two 3)", INT_TYPE, 5L);
    }

    @Test
    public void typeDefsIdentityFn() throws Exception {
        Var var = (Var) testEvalsAction(PLUS_ENV, "(:: identity (Fn a a))").right.value;
        Type.FnType type = (Type.FnType) var.declaredType;
        assertEquals(1, type.paramTypes.size());
        Type returnType = type.returnType;
        assertTrue(returnType instanceof Type.TypeVar);
        assertEquals(type.paramTypes.get(0), returnType);
    }

    @Test
    public void testsTypeDefdFn() throws Exception {
        EvalResult typeDefResult = testEvalsAction(PLUS_ENV, "(:: double (Fn Int Int))").right;
        EvalResult defResult = testEvalsAction(typeDefResult.env, "(def (double x) (+ x x))").right;

        Env env = defResult.env;

        Type doubleType = fnType(vectorOf(INT_TYPE), INT_TYPE);

        assertEquals(doubleType, env.vars.get(symbol("double")).declaredType);
        testEvalsValue(env, "(double 4)", INT_TYPE, 8);
    }
}
