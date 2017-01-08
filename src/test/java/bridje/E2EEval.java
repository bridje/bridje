package bridje;

import bridje.analyser.Expr;
import bridje.reader.Form;
import bridje.reader.LCReader;
import bridje.runtime.DataType;
import bridje.runtime.Env;
import bridje.runtime.EvalResult;
import bridje.runtime.NS;
import bridje.util.Pair;
import org.junit.Test;
import org.pcollections.HashTreePMap;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.time.Instant;

import static bridje.analyser.Analyser.analyse;
import static bridje.compiler.Compiler.compile;
import static bridje.reader.FormReader.read;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.USER;
import static bridje.runtime.NS.ns;
import static bridje.runtime.Symbol.symbol;
import static bridje.runtime.VarUtil.FOO_NS;
import static bridje.runtime.VarUtil.PLUS_ENV;
import static bridje.util.Pair.pair;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class E2EEval {

    private Pair<Expr, EvalResult> eval(Env env, NS ns, String code) {
        Form form = read(LCReader.fromString(code));

        Expr expr = analyse(env, ns, form);

        return pair(expr, compile(env, expr));
    }

    private Pair<Expr, EvalResult> eval(Env env, String code) {
        return eval(env, USER, code);
    }

    private void testEvalValue(Env env, String code, Object expectedResult) {
        Pair<Expr, EvalResult> result = eval(env, code);

        assertEquals(env, result.right.env);
        assertEquals(expectedResult, result.right.value);
    }

    @Test
    public void evalsInt() throws Exception {
        testEvalValue(PLUS_ENV, "4", 4L);
    }

    @Test
    public void evalsLet() throws Exception {
        testEvalValue(PLUS_ENV, "(let [x 4, y 3] (+ x (+ x y)))", 11L);
    }

    @Test
    public void evalsAnonymousFn() throws Exception {
        testEvalValue(PLUS_ENV, "(let [double (fn (x) (+ x x))] (double 4))", 8L);
    }

    @Test
    public void evalsFnWithClosedOverLocals() throws Exception {
        testEvalValue(PLUS_ENV, "(let [y 2, add-y (fn (x) (+ x y))] (add-y 4))", 6L);
    }

    @Test
    public void evalsMap() throws Exception {
        testEvalValue(PLUS_ENV, "^{\"Alice\" 4, \"Bob\" 3}",
            HashTreePMap.singleton("Alice", 4L).plus("Bob", 3L));
    }

    @Test
    public void testsDefFn() throws Exception {
//        Pair<Expr, EvalResult> result = eval(PLUS_ENV, "(def (double x) (+ x x))");
//        assertEquals(fnType(vectorOf(INT_TYPE), INT_TYPE),
//            ((Expr.DefExpr) result.left).body.type);
//
//        Env resultEnv = result.right.env;
//
//        testEvalValue(resultEnv, "(double 12)", 24L);
        throw new UnsupportedOperationException();
    }

    @Test
    public void testDefLetFn() throws Exception {
//        // we can't (currently) compile straight to a method here - would be a static analysis optimisation
//        // but at least we can avoid bugging out...
//        Pair<Expr, EvalResult> result = eval(PLUS_ENV, ("(def add-two (let [y 2] (fn (x) (+ x y))))"));
//        assertEquals(fnType(vectorOf(INT_TYPE), INT_TYPE), ((Expr.DefExpr) result.left).body.type);
//
//        testEvalValue(result.right.env, "(add-two 3)", 5L);

        throw new UnsupportedOperationException();
    }

    @Test
    public void testsSimpleEnum() throws Exception {
        EvalResult defDataResult = eval(PLUS_ENV, "(defdata Month Jan Feb Mar)").right;

        Pair<Expr, EvalResult> result = eval(defDataResult.env, "Mar");

        throw new UnsupportedOperationException();
    }

    @Test
    public void testSimpleUnion() throws Exception {
        EvalResult defDataResult = eval(PLUS_ENV, "(defdata IntOrString (AnInt Int) (AString Str))").right;

        Pair<Expr, EvalResult> result = eval(defDataResult.env, "(AnInt 4)");

        Object value = result.right.value;
        assertEquals(4L, value.getClass().getDeclaredField("field$$0").get(value));
    }

    @Test
    public void testIntList() throws Exception {
        EvalResult defDataResult = eval(PLUS_ENV, "(defdata IntList (IntListCons Int IntList) EmptyIntList)").right;

        Pair<Expr, EvalResult> result = eval(defDataResult.env, "(IntListCons 4 (IntListCons 5 EmptyIntList))");

        Object intList = result.right.value;
        Field field0 = intList.getClass().getDeclaredField("field$$0");
        Object nestedIntList = intList.getClass().getDeclaredField("field$$1").get(intList);

        assertEquals(4L, field0.get(intList));
        assertEquals(5L, field0.get(nestedIntList));
    }

    @Test
    public void testPolymorphicList() throws Exception {
        EvalResult defDataResult = eval(PLUS_ENV, "(defdata (Foo a) (FooCons a (Foo a)) EmptyFoo)").right;

        DataType dataType = (DataType) defDataResult.value;

        Pair<Expr, EvalResult> result = eval(defDataResult.env, "(FooCons 4 (FooCons 5 EmptyFoo))");

        Object fooList = result.right.value;
        Field field0 = fooList.getClass().getDeclaredField("field$$0");
        Object nestedFooList = fooList.getClass().getDeclaredField("field$$1").get(fooList);

        assertEquals(4L, field0.get(fooList));
        assertEquals(5L, field0.get(nestedFooList));
    }

    @Test
    public void testNSAliases() throws Exception {
        EvalResult evalResult = eval(PLUS_ENV, "(ns my-ns {aliases {u user}})").right;

        assertEquals(ns("user"), evalResult.env.nsEnvs.get(ns("my-ns")).aliases.get(symbol("u")));

        Pair<Expr, EvalResult> result = eval(evalResult.env, ns("my-ns"), "(u/+ 4 3)");

        assertEquals(7L, result.right.value);
    }

    @Test
    public void testNSRefers() throws Exception {
        EvalResult evalResult = eval(PLUS_ENV, "(ns my-ns {refers {user [+]}})").right;

        assertEquals(fqSym(USER, symbol("+")), evalResult.env.nsEnvs.get(ns("my-ns")).refers.get(symbol("+")));

        Pair<Expr, EvalResult> result = eval(evalResult.env, ns("my-ns"), "(+ 4 3)");

        assertEquals(7L, result.right.value);
    }

    @Test
    public void testJavaStaticTypeDef() throws Exception {
        Env env = eval(PLUS_ENV, FOO_NS, "(defj bool->str String valueOf boolean String)").right.env;

        Pair<Expr, EvalResult> result = eval(env, FOO_NS, "(bool->str true)");
        assertEquals(result.right.value, "true");
    }

    @Test
    public void testJavaInstanceTypeDef() throws Exception {
        Env env = eval(PLUS_ENV, FOO_NS, "(defj trim-str String .trim String String)").right.env;

        Pair<Expr, EvalResult> result = eval(env, FOO_NS, "(trim-str \"   foo  \")");
        assertEquals(result.right.value, "foo");
    }

    @Test
    public void testJavaIOTypeDef() throws Exception {
        NS myNS = ns("my-ns");

        EvalResult evalResult = eval(PLUS_ENV, "(ns my-ns {imports {java.time [Instant]}})").right;

        Env env = eval(evalResult.env, myNS, "(defj now Instant now (Fn Instant))").right.env;

        Pair<Expr, EvalResult> result = eval(env, myNS, "now");

        assertTrue(result.right.value instanceof Instant);
    }

    @Test
    public void testMoreJavaInterop() throws Exception {
        NS myNS = ns("my-ns");

        Env env = eval(PLUS_ENV, "(ns my-ns {imports {java.io [PrintWriter PrintStream OutputStream], java.lang [System]}})").right.env;
        env = eval(env, myNS, "(defj new-print-writer PrintWriter new (OutputStream) PrintWriter)").right.env;
        env = eval(env, myNS, "(defj system-out System -out () PrintStream)").right.env;

        Pair<Expr, EvalResult> result = eval(env, myNS, "(new-print-writer system-out)");

        assertTrue(result.right.value instanceof PrintWriter);
    }
}
