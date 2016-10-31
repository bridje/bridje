package rho.compiler;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.analyser.LocalVar;
import rho.analyser.ValueExpr;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.runtime.Var;

import java.lang.invoke.MethodHandle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static rho.Util.setOf;
import static rho.Util.vectorOf;
import static rho.analyser.ActionExpr.DefExpr.defExpr;
import static rho.analyser.LocalVar.localVar;
import static rho.analyser.ValueExpr.BoolExpr.boolExpr;
import static rho.analyser.ValueExpr.CallExpr.callExpr;
import static rho.analyser.ValueExpr.GlobalVarExpr.globalVarExpr;
import static rho.analyser.ValueExpr.IfExpr.ifExpr;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.LetExpr.LetBinding.letBinding;
import static rho.analyser.ValueExpr.LetExpr.letExpr;
import static rho.analyser.ValueExpr.LocalVarExpr.localVarExpr;
import static rho.analyser.ValueExpr.SetExpr.setExpr;
import static rho.analyser.ValueExpr.StringExpr.stringExpr;
import static rho.analyser.ValueExpr.VarCallExpr.varCallExpr;
import static rho.analyser.ValueExpr.VectorExpr.vectorExpr;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.*;
import static rho.types.ActionType.DefType.defType;
import static rho.types.ValueType.SetType.setType;
import static rho.types.ValueType.SimpleType.*;
import static rho.types.ValueType.VectorType.vectorType;

public class CompilerTest {

    @Test
    public void compilesBoolean() throws Exception {
        assertEquals(false, Compiler.compile(Env.env(), boolExpr(false), BOOL_TYPE).value);
    }

    @Test
    public void compilesString() throws Exception {
        assertEquals("hello world!", Compiler.compile(Env.env(), stringExpr("hello world!"), STRING_TYPE).value);
    }

    @Test
    public void compilesInt() throws Exception {
        assertEquals(513L, Compiler.compile(Env.env(), intExpr(513), INT_TYPE).value);
    }

    @Test
    public void compilesVector() throws Exception {
        assertEquals(vectorOf("Hello", "World!"), Compiler.compile(Env.env(), vectorExpr(vectorOf(stringExpr("Hello"), stringExpr("World!"))), vectorType(STRING_TYPE)).value);
    }

    @Test
    public void compilesSet() throws Exception {
        assertEquals(setOf("Hello", "World!"), Compiler.compile(Env.env(), setExpr(vectorOf(stringExpr("Hello"), stringExpr("World!"))), setType(STRING_TYPE)).value);
    }

    @Test
    public void compilesPlus() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        assertEquals(3L, Compiler.compile(env, varCallExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2))), INT_TYPE).value);
    }

    @Test
    public void compilesIf() throws Exception {
        assertEquals("is false", Compiler.compile(Env.env(), ifExpr(boolExpr(false), stringExpr("is true"), stringExpr("is false")), STRING_TYPE).value);
    }

    @Test
    public void compilesLet() throws Exception {
        LocalVar x = localVar(symbol("x"));
        LocalVar y = localVar(symbol("y"));

        ValueExpr.LetExpr letExpr = letExpr(
            vectorOf(
                letBinding(x, intExpr(4)),
                letBinding(y, intExpr(3))),
            vectorExpr(vectorOf(localVarExpr(x), localVarExpr(y))));

        assertEquals(vectorOf(4L, 3L), Compiler.compile(Env.env(), letExpr, vectorType(INT_TYPE)).value);
    }

    @Test
    public void compilesGlobalVar() throws Throwable {
        MethodHandle handle = (MethodHandle) Compiler.compile(Env.env(), globalVarExpr(PLUS_VAR), PLUS_TYPE).value;

        assertEquals(3L, handle.invoke(1L, 2L));
    }

    @Test
    public void compilesDefValue() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        EvalResult evalResult = Compiler.compile(env, defExpr(symbol("three"), varCallExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2)))), defType(INT_TYPE));
        assertEquals(3L, evalResult.value);

        Var var = evalResult.env.vars.get(symbol("three"));
        assertNotNull(var);
        assertEquals(INT_TYPE, var.type);

        assertEquals(3L, Compiler.compile(evalResult.env, globalVarExpr(var), INT_TYPE).value);
    }

    @Test
    public void compilesFirstClassFn() throws Exception {
        LocalVar x = localVar(symbol("x"));
        assertEquals(3L, Compiler.compile(PLUS_ENV,
            letExpr(
                vectorOf(
                    letBinding(x, globalVarExpr(PLUS_VAR))),
                callExpr(vectorOf(localVarExpr(x), intExpr(1), intExpr(2)))), INT_TYPE).value);

    }
}