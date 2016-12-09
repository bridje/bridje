package bridje;

import bridje.analyser.Expr;
import bridje.reader.Form;
import bridje.reader.LCReader;
import bridje.runtime.DataType;
import bridje.runtime.Env;
import bridje.runtime.EvalResult;
import bridje.runtime.Var;
import bridje.types.HMTypeChecker;
import bridje.types.Type;
import bridje.util.Pair;
import org.junit.Test;

import java.lang.reflect.Field;

import static bridje.Util.vectorOf;
import static bridje.analyser.Analyser.analyse;
import static bridje.compiler.Compiler.compile;
import static bridje.reader.FormReader.read;
import static bridje.runtime.Symbol.symbol;
import static bridje.runtime.VarUtil.PLUS_ENV;
import static bridje.types.Type.FnType.fnType;
import static bridje.types.Type.SimpleType.INT_TYPE;
import static bridje.util.Pair.pair;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class E2EEval {

    private Pair<Expr<Type>, EvalResult> evalValue(Env env, String code) {
        Form form = read(LCReader.fromString(code));

        Expr<Void> expr = analyse(env, form);

        Expr<Type> typedExpr = HMTypeChecker.typeExpr(expr);

        return pair(typedExpr, compile(env, typedExpr));
    }

    private void testEvalValue(Env env, String code, Type expectedType, Object expectedResult) {
        Pair<Expr<Type>, EvalResult> result = evalValue(env, code);

        assertEquals(expectedType, result.left.type);
        assertEquals(env, result.right.env);
        assertEquals(expectedResult, result.right.value);
    }

    private Pair<Expr<Type>, EvalResult> evalAction(Env env, String code) {
        Form form = read(LCReader.fromString(code));

        Expr<Void> expr = analyse(env, form);

        Expr<Type> typedExpr = HMTypeChecker.typeExpr(expr);

        return pair(typedExpr, compile(env, typedExpr));
    }

    @Test
    public void evalsLet() throws Exception {
        testEvalValue(PLUS_ENV, "(let [x 4, y 3] (+ x (+ x y)))", INT_TYPE, 11L);
    }

    @Test
    public void evalsAnonymousFn() throws Exception {
        testEvalValue(PLUS_ENV, "(let [double (fn (x) (+ x x))] (double 4))", INT_TYPE, 8L);
    }

    @Test
    public void evalsFnWithClosedOverLocals() throws Exception {
        testEvalValue(PLUS_ENV, "(let [y 2, add-y (fn (x) (+ x y))] (add-y 4))", INT_TYPE, 6L);
    }

    @Test
    public void testsDefFn() throws Exception {
        Pair<Expr<Type>, EvalResult> result = evalAction(PLUS_ENV, "(def (double x) (+ x x))");
        assertEquals(fnType(vectorOf(INT_TYPE), INT_TYPE),
            ((Expr.DefExpr<Type>) result.left).body.type);

        Env resultEnv = result.right.env;

        testEvalValue(resultEnv, "(double 12)", INT_TYPE, 24L);
    }

    @Test
    public void testDefLetFn() throws Exception {
        // we can't (currently) compile straight to a method here - would be a static analysis optimisation
        // but at least we can avoid bugging out...
        Pair<Expr<Type>, EvalResult> result = evalAction(PLUS_ENV, ("(def add-two (let [y 2] (fn (x) (+ x y))))"));
        assertEquals(fnType(vectorOf(INT_TYPE), INT_TYPE), ((Expr.DefExpr<Type>) result.left).body.type);

        testEvalValue(result.right.env, "(add-two 3)", INT_TYPE, 5L);
    }

    @Test
    public void typeDefsIdentityFn() throws Exception {
        Var var = (Var) evalAction(PLUS_ENV, "(:: identity (Fn a a))").right.value;
        Type.TypeVar a = new Type.TypeVar();

        assertTrue(var.declaredType.alphaEquivalentTo(new Type.FnType(vectorOf(a), a)));
    }

    @Test
    public void testsTypeDefdFn() throws Exception {
        EvalResult typeDefResult = evalAction(PLUS_ENV, "(:: double (Fn Int Int))").right;
        EvalResult defResult = evalAction(typeDefResult.env, "(def (double x) (+ x x))").right;

        Env env = defResult.env;

        Type doubleType = fnType(vectorOf(INT_TYPE), INT_TYPE);

        assertEquals(doubleType, env.vars.get(symbol("double")).declaredType);
        testEvalValue(env, "(double 4)", INT_TYPE, 8L);
    }

    @Test
    public void testsSimpleEnum() throws Exception {
        EvalResult defDataResult = evalAction(PLUS_ENV, "(defdata Month Jan Feb Mar)").right;

        Pair<Expr<Type>, EvalResult> result = evalValue(defDataResult.env, "Mar");

        assertEquals(new Type.DataTypeType(symbol("Month"), null), result.left.type);
    }

    @Test
    public void testSimpleUnion() throws Exception {
        EvalResult defDataResult = evalAction(PLUS_ENV, "(defdata IntOrString (AnInt Int) (AString Str))").right;

        Pair<Expr<Type>, EvalResult> result = evalValue(defDataResult.env, "(AnInt 4)");

        assertEquals(new Type.DataTypeType(symbol("IntOrString"), null), result.left.type);

        Object value = result.right.value;
        assertEquals(4L, value.getClass().getDeclaredField("field$$0").get(value));
    }

    @Test
    public void testIntList() throws Exception {
        EvalResult defDataResult = evalAction(PLUS_ENV, "(defdata IntList (IntListCons Int IntList) EmptyIntList)").right;

        Pair<Expr<Type>, EvalResult> result = evalValue(defDataResult.env, "(IntListCons 4 (IntListCons 5 EmptyIntList))");

        assertEquals(new Type.DataTypeType(symbol("IntList"), null), result.left.type);

        Object intList = result.right.value;
        Field field0 = intList.getClass().getDeclaredField("field$$0");
        Object nestedIntList = intList.getClass().getDeclaredField("field$$1").get(intList);

        assertEquals(4L, field0.get(intList));
        assertEquals(5L, field0.get(nestedIntList));
    }

    @Test
    public void testPolymorphicList() throws Exception {
        EvalResult defDataResult = evalAction(PLUS_ENV, "(defdata (Foo a) (FooCons a (Foo a)) EmptyFoo)").right;

        DataType<Type> dataType = (DataType) defDataResult.value;

        assertTrue(dataType.typeVars.size() == 1);

        Type.TypeVar a = new Type.TypeVar();

        Type.AppliedType appliedType = new Type.AppliedType(new Type.DataTypeType(symbol("Foo"), null), vectorOf(a));
        assertTrue(defDataResult.env.vars.get(symbol("FooCons")).declaredType
            .alphaEquivalentTo(fnType(vectorOf(a, appliedType), appliedType)));

        Pair<Expr<Type>, EvalResult> result = evalValue(defDataResult.env, "(FooCons 4 (FooCons 5 EmptyFoo))");


        assertTrue(new Type.AppliedType(new Type.DataTypeType(symbol("Foo"), null), vectorOf(INT_TYPE)).alphaEquivalentTo(result.left.type));

        Object fooList = result.right.value;
        Field field0 = fooList.getClass().getDeclaredField("field$$0");
        Object nestedFooList = fooList.getClass().getDeclaredField("field$$1").get(fooList);

        assertEquals(4L, field0.get(fooList));
        assertEquals(5L, field0.get(nestedFooList));
    }
}
