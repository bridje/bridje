package bridje.compiler;

import bridje.analyser.Expr;
import bridje.analyser.LocalVar;
import bridje.runtime.*;
import org.junit.Test;
import org.pcollections.HashTreePMap;

import java.lang.invoke.MethodHandle;
import java.time.Instant;

import static bridje.Util.setOf;
import static bridje.Util.vectorOf;
import static bridje.analyser.ExprUtil.*;
import static bridje.compiler.Compiler.compile;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.USER;
import static bridje.runtime.NS.ns;
import static bridje.runtime.Symbol.symbol;
import static bridje.runtime.VarUtil.PLUS_ENV;
import static bridje.runtime.VarUtil.PLUS_VAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CompilerTest {

    @Test
    public void compilesBoolean() throws Exception {
        assertEquals(false, compile(Env.env(), boolExpr(false)).value);
    }

    @Test
    public void compilesString() throws Exception {
        assertEquals("hello world!", compile(Env.env(), stringExpr("hello world!")).value);
    }

    @Test
    public void compilesInt() throws Exception {
        assertEquals(513L, compile(Env.env(), intExpr(513)).value);
    }

    @Test
    public void compilesVector() throws Exception {
        assertEquals(vectorOf(4L, 5L), compile(Env.env(), vectorExpr(vectorOf(intExpr(4), intExpr(5)))).value);
    }

    @Test
    public void compilesSet() throws Exception {
        assertEquals(setOf("Hello", "World!"), compile(Env.env(), setExpr(vectorOf(stringExpr("Hello"), stringExpr("World!")))).value);
    }

    @Test
    public void compilesPlus() throws Exception {
        assertEquals(3L, compile(PLUS_ENV, varCallExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2)))).value);
    }

    @Test
    public void compilesIf() throws Exception {
        assertEquals("is false", compile(Env.env(), ifExpr(
            boolExpr(false),
            stringExpr("is true"),
            stringExpr("is false"))).value);
    }

    @Test
    public void compilesLet() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));

        Expr.LetExpr letExpr = letExpr(
            vectorOf(
                letBinding(x, intExpr(4)),
                letBinding(y, intExpr(3))),
            vectorExpr(vectorOf(localVarExpr(x), localVarExpr(y))));

        assertEquals(vectorOf(4L, 3L), compile(Env.env(), letExpr).value);
    }

    @Test
    public void compilesGlobalVar() throws Throwable {
        MethodHandle handle = (MethodHandle) compile(Env.env(), globalVarExpr(PLUS_VAR)).value;

        assertEquals(3L, handle.invoke(1L, 2L));
    }

    @Test
    public void compilesNS() throws Exception {
        Symbol plusSym = symbol("+");
        EvalResult evalResult = compile(PLUS_ENV, new Expr.NSExpr(null, ns("my-ns"),
            HashTreePMap.singleton(symbol("u"), ns("user")),
            HashTreePMap.singleton(plusSym, fqSym(USER, plusSym)),
            HashTreePMap.singleton(symbol("Instant"), Instant.class)));
        NSEnv nsEnv = evalResult.env.nsEnvs.get(ns("my-ns"));
        assertEquals(USER, nsEnv.aliases.get(symbol("u")));
        assertEquals(fqSym(USER, plusSym), nsEnv.refers.get(plusSym));
        assertEquals(Instant.class, nsEnv.imports.get(symbol("Instant")));
    }

    @Test
    public void compilesDefValue() throws Exception {
        EvalResult evalResult = compile(PLUS_ENV, defExpr(symbol("three"),
            varCallExpr(
                PLUS_VAR,
                vectorOf(
                    intExpr(1),
                    intExpr(2)))));
        assertEquals(3L, ((Var) evalResult.value).valueField.get(null));

        Var var = evalResult.env.resolveVar(USER, symbol("three")).orElse(null);
        assertNotNull(var);

        assertEquals(3L, compile(evalResult.env, globalVarExpr(var)).value);
    }

    @Test
    public void compilesFirstClassFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        assertEquals(3L, compile(PLUS_ENV,
            letExpr(
                vectorOf(
                    letBinding(x, globalVarExpr(PLUS_VAR))),
                callExpr(
                    vectorOf(
                        localVarExpr(x),
                        intExpr(1),
                        intExpr(2))))).value);

    }

    @Test
    public void compilesInlineFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        assertEquals(4L, compile(PLUS_ENV,
            callExpr(
                vectorOf(
                    fnExpr(
                        vectorOf(x),
                        callExpr(
                            vectorOf(
                                globalVarExpr(PLUS_VAR),
                                localVarExpr(x),
                                localVarExpr(x)))),
                    intExpr(2)))).value);
    }

    @Test
    public void compilesInlineFnWithClosedOverVars() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));
        LocalVar addY = new LocalVar(symbol("add-y"));

        assertEquals(5L, compile(PLUS_ENV,
            letExpr(
                vectorOf(
                    letBinding(y, intExpr(3)),
                    letBinding(addY, fnExpr(
                        vectorOf(x),
                        callExpr(
                            vectorOf(
                                globalVarExpr(PLUS_VAR),
                                localVarExpr(x),
                                localVarExpr(y)))))),

                callExpr(
                    vectorOf(
                        localVarExpr(addY),
                        intExpr(2))))
        ).value);
    }

    @Test
    public void compilesParametricDataType() throws Exception {
//        FQSymbol foo = fqSym(USER, symbol("Foo"));
//        DataTypeType dataTypeType = new DataTypeType(foo, null);
//        TypeVar a = new TypeVar();
//
//        AppliedType appliedType = new AppliedType(dataTypeType, vectorOf(a));
//
//        EvalResult result = Compiler.compile(PLUS_ENV, new Expr.DefDataExpr(null,
//            new DataType(foo, vectorOf(a),
//                vectorOf(
//                    new DataTypeConstructor.VectorConstructor(
//                        fqSym(USER, symbol("FooCons")), paramNames)))));
//
//        Env newEnv = result.env;
//
//        Type type = newEnv.dataTypes.get(foo).constructors.get(0).type;
//        assertTrue(type.alphaEquivalentTo(fnType(vectorOf(a, appliedType), appliedType)));

        throw new UnsupportedOperationException();
    }
}