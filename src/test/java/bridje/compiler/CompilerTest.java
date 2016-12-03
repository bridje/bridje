package bridje.compiler;

import bridje.analyser.Expr;
import bridje.analyser.LocalVar;
import bridje.runtime.*;
import bridje.types.Type;
import org.junit.Test;

import java.lang.invoke.MethodHandle;

import static bridje.Util.setOf;
import static bridje.Util.vectorOf;
import static bridje.analyser.ExprUtil.*;
import static bridje.runtime.Symbol.symbol;
import static bridje.runtime.VarUtil.*;
import static bridje.types.Type.FnType.fnType;
import static bridje.types.Type.SetType.setType;
import static bridje.types.Type.SimpleType.*;
import static bridje.types.Type.VectorType.vectorType;
import static org.junit.Assert.*;

public class CompilerTest {

    @Test
    public void compilesBoolean() throws Exception {
        assertEquals(false, Compiler.compile(Env.env(), boolExpr(BOOL_TYPE, false)).value);
    }

    @Test
    public void compilesString() throws Exception {
        assertEquals("hello world!", Compiler.compile(Env.env(), stringExpr(STRING_TYPE, "hello world!")).value);
    }

    @Test
    public void compilesInt() throws Exception {
        assertEquals(513L, Compiler.compile(Env.env(), intExpr(INT_TYPE, 513)).value);
    }

    @Test
    public void compilesVector() throws Exception {
        assertEquals(vectorOf("Hello", "World!"), Compiler.compile(Env.env(), vectorExpr(vectorType(STRING_TYPE), vectorOf(stringExpr(STRING_TYPE, "Hello"), stringExpr(STRING_TYPE, "World!")))).value);
    }

    @Test
    public void compilesSet() throws Exception {
        assertEquals(setOf("Hello", "World!"), Compiler.compile(Env.env(), setExpr(setType(STRING_TYPE), vectorOf(stringExpr(STRING_TYPE, "Hello"), stringExpr(STRING_TYPE, "World!")))).value);
    }

    @Test
    public void compilesPlus() throws Exception {
        assertEquals(3L, Compiler.compile(PLUS_ENV, varCallExpr(INT_TYPE, PLUS_VAR, vectorOf(intExpr(INT_TYPE, 1), intExpr(INT_TYPE, 2)))).value);
    }

    @Test
    public void compilesIf() throws Exception {
        assertEquals("is false", Compiler.compile(Env.env(), ifExpr(STRING_TYPE,
            boolExpr(BOOL_TYPE, false),
            stringExpr(STRING_TYPE, "is true"),
            stringExpr(STRING_TYPE, "is false"))).value);
    }

    @Test
    public void compilesLet() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));

        Expr.LetExpr<Type> letExpr = letExpr(vectorType(INT_TYPE),
            vectorOf(
                letBinding(x, intExpr(INT_TYPE, 4)),
                letBinding(y, intExpr(INT_TYPE, 3))),
            vectorExpr(vectorType(INT_TYPE), vectorOf(localVarExpr(INT_TYPE, x), localVarExpr(INT_TYPE, y))));

        assertEquals(vectorOf(4L, 3L), Compiler.compile(Env.env(), letExpr).value);
    }

    @Test
    public void compilesGlobalVar() throws Throwable {
        MethodHandle handle = (MethodHandle) Compiler.compile(Env.env(), globalVarExpr(PLUS_TYPE, PLUS_VAR)).value;

        assertEquals(3L, handle.invoke(1L, 2L));
    }

    @Test
    public void compilesDefValue() throws Exception {
        EvalResult evalResult = Compiler.compile(PLUS_ENV, defExpr(ENV_IO, symbol("three"),
            varCallExpr(
                INT_TYPE,
                PLUS_VAR,
                vectorOf(
                    intExpr(INT_TYPE, 1),
                    intExpr(INT_TYPE, 2)))));
        assertEquals(3L, ((Var) evalResult.value).valueField.get(null));

        Var var = evalResult.env.vars.get(symbol("three"));
        assertNotNull(var);
        assertEquals(INT_TYPE, var.inferredType);

        assertEquals(3L, Compiler.compile(evalResult.env, globalVarExpr(INT_TYPE, var)).value);
    }

    @Test
    public void compilesFirstClassFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        assertEquals(3L, Compiler.compile(PLUS_ENV,
            letExpr(INT_TYPE,
                vectorOf(
                    letBinding(x, globalVarExpr(PLUS_TYPE, PLUS_VAR))),
                callExpr(
                    INT_TYPE,
                    vectorOf(
                        localVarExpr(PLUS_TYPE, x),
                        intExpr(INT_TYPE, 1),
                        intExpr(INT_TYPE, 2))))).value);

    }

    @Test
    public void compilesInlineFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        assertEquals(4L, Compiler.compile(PLUS_ENV,
            callExpr(INT_TYPE,
                vectorOf(
                    fnExpr(fnType(vectorOf(INT_TYPE), INT_TYPE),
                        vectorOf(x),
                        callExpr(INT_TYPE,
                            vectorOf(
                                globalVarExpr(PLUS_TYPE, PLUS_VAR),
                                localVarExpr(INT_TYPE, x),
                                localVarExpr(INT_TYPE, x)))),
                    intExpr(INT_TYPE, 2)))).value);
    }

    @Test
    public void compilesInlineFnWithClosedOverVars() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));
        LocalVar addY = new LocalVar(symbol("add-y"));

        assertEquals(5L, Compiler.compile(PLUS_ENV,
            letExpr(INT_TYPE,
                vectorOf(
                    letBinding(y, intExpr(INT_TYPE, 3)),
                    letBinding(addY, fnExpr(fnType(vectorOf(INT_TYPE), INT_TYPE),
                        vectorOf(x),
                        callExpr(INT_TYPE,
                            vectorOf(
                                globalVarExpr(PLUS_TYPE, PLUS_VAR),
                                localVarExpr(INT_TYPE, x),
                                localVarExpr(INT_TYPE, y)))))),

                callExpr(INT_TYPE,
                    vectorOf(
                        localVarExpr(fnType(vectorOf(INT_TYPE), INT_TYPE), addY),
                        intExpr(INT_TYPE, 2))))
        ).value);
    }

    @Test
    public void compilesTypeDef() throws Exception {
        Symbol sym = symbol("double");
        Type doubleType = fnType(vectorOf(INT_TYPE), INT_TYPE);
        EvalResult result = Compiler.compile(PLUS_ENV, new Expr.TypeDefExpr<>(null, ENV_IO, sym, doubleType));

        assertEquals(doubleType, result.env.vars.get(sym).declaredType);

    }

    @Test
    public void compilesParametricDataType() throws Exception {
        Symbol foo = symbol("Foo");
        DataTypeType dataTypeType = new DataTypeType(foo, null);
        TypeVar a = new TypeVar();

        AppliedType appliedType = new AppliedType(dataTypeType, vectorOf(a));

        EvalResult result = Compiler.compile(PLUS_ENV, new Expr.DefDataExpr<>(null,
            ENV_IO, new DataType<>(appliedType, foo, vectorOf(a),
            vectorOf(
                new DataTypeConstructor.VectorConstructor<>(
                    fnType(vectorOf(a, appliedType), appliedType),
                    symbol("FooCons"),
                    vectorOf(a))))));

        Env newEnv = result.env;

        Type type = newEnv.dataTypes.get(foo).constructors.get(0).type;
        assertTrue(type.alphaEquivalentTo(fnType(vectorOf(a, appliedType), appliedType)));

    }
}