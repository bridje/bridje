package rho.compiler;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.analyser.LocalVar;
import rho.analyser.ValueExpr;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.runtime.Var;
import rho.types.TypedExprData;

import java.lang.invoke.MethodHandle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static rho.Util.setOf;
import static rho.Util.vectorOf;
import static rho.analyser.ExprUtil.*;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.*;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.*;
import static rho.types.Type.VectorType.vectorType;

public class CompilerTest {

    @Test
    public void compilesBoolean() throws Exception {
        assertEquals(false, Compiler.compile(Env.env(), boolExpr(new TypedExprData(BOOL_TYPE, null), false)).value);
    }

    @Test
    public void compilesString() throws Exception {
        assertEquals("hello world!", Compiler.compile(Env.env(), stringExpr(new TypedExprData(STRING_TYPE, null), "hello world!")).value);
    }

    @Test
    public void compilesInt() throws Exception {
        assertEquals(513L, Compiler.compile(Env.env(), intExpr(new TypedExprData(INT_TYPE, null), 513)).value);
    }

    @Test
    public void compilesVector() throws Exception {
        assertEquals(vectorOf("Hello", "World!"), Compiler.compile(Env.env(), vectorExpr(new TypedExprData(vectorType(STRING_TYPE), null), vectorOf(stringExpr(new TypedExprData(STRING_TYPE, null), "Hello"), stringExpr(new TypedExprData(STRING_TYPE, null), "World!")))).value);
    }

    @Test
    public void compilesSet() throws Exception {
        assertEquals(setOf("Hello", "World!"), Compiler.compile(Env.env(), setExpr(new TypedExprData(setType(STRING_TYPE), null), vectorOf(stringExpr(new TypedExprData(STRING_TYPE, null), "Hello"), stringExpr(new TypedExprData(STRING_TYPE, null), "World!")))).value);
    }

    @Test
    public void compilesPlus() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        assertEquals(3L, Compiler.compile(env, varCallExpr(new TypedExprData(INT_TYPE, null), PLUS_VAR, vectorOf(intExpr(new TypedExprData(INT_TYPE, null), 1), intExpr(new TypedExprData(INT_TYPE, null), 2)))).value);
    }

    @Test
    public void compilesIf() throws Exception {
        assertEquals("is false", Compiler.compile(Env.env(), ifExpr(new TypedExprData(STRING_TYPE, null),
            boolExpr(new TypedExprData(BOOL_TYPE, null), false),
            stringExpr(new TypedExprData(STRING_TYPE, null), "is true"),
            stringExpr(new TypedExprData(STRING_TYPE, null), "is false"))).value);
    }

    @Test
    public void compilesLet() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));

        ValueExpr.LetExpr<TypedExprData> letExpr = letExpr(new TypedExprData(vectorType(INT_TYPE), null),
            vectorOf(
                letBinding(x, intExpr(new TypedExprData(INT_TYPE, null), 4)),
                letBinding(y, intExpr(new TypedExprData(INT_TYPE, null), 3))),
            vectorExpr(new TypedExprData(vectorType(INT_TYPE), null), vectorOf(localVarExpr(new TypedExprData(INT_TYPE, null), x), localVarExpr(new TypedExprData(INT_TYPE, null), y))));

        assertEquals(vectorOf(4L, 3L), Compiler.compile(Env.env(), letExpr).value);
    }

    @Test
    public void compilesGlobalVar() throws Throwable {
        MethodHandle handle = (MethodHandle) Compiler.compile(Env.env(), globalVarExpr(new TypedExprData(PLUS_TYPE, null), PLUS_VAR)).value;

        assertEquals(3L, handle.invoke(1L, 2L));
    }

    @Test
    public void compilesDefValue() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        EvalResult evalResult = Compiler.compile(env, defExpr(symbol("three"),
            varCallExpr(
                new TypedExprData(INT_TYPE, null),
                PLUS_VAR,
                vectorOf(
                    intExpr(new TypedExprData(INT_TYPE, null), 1),
                    intExpr(new TypedExprData(INT_TYPE, null), 2)))));
        assertEquals(3L, evalResult.value);

        Var var = evalResult.env.vars.get(symbol("three"));
        assertNotNull(var);
        assertEquals(INT_TYPE, var.type);

        assertEquals(3L, Compiler.compile(evalResult.env, globalVarExpr(new TypedExprData(INT_TYPE, null), var)).value);
    }

    @Test
    public void compilesFirstClassFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        assertEquals(3L, Compiler.compile(PLUS_ENV,
            letExpr(new TypedExprData(INT_TYPE, null),
                vectorOf(
                    letBinding(x, globalVarExpr(new TypedExprData(PLUS_TYPE, null), PLUS_VAR))),
                callExpr(
                    new TypedExprData(INT_TYPE, null),
                    vectorOf(
                        localVarExpr(new TypedExprData(PLUS_TYPE, null), x),
                        intExpr(new TypedExprData(INT_TYPE, null), 1),
                        intExpr(new TypedExprData(INT_TYPE, null), 2))))).value);

    }
}