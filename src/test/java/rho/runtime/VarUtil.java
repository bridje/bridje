package rho.runtime;

import org.pcollections.HashTreePMap;
import rho.types.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static rho.Util.vectorOf;
import static rho.runtime.Symbol.symbol;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SimpleType.INT_TYPE;

public class VarUtil {

    public static long plusFn(long a, long b) {
        return a + b;
    }

    public static final MethodHandle PLUS_HANDLE;
    public static final Field PLUS_VALUE_FIELD;
    public static final Method PLUS_FN_METHOD;

    static {
        try {
            PLUS_HANDLE = MethodHandles.publicLookup().findStatic(VarUtil.class, "plusFn", MethodType.methodType(Long.TYPE, Long.TYPE, Long.TYPE));
            PLUS_VALUE_FIELD = VarUtil.class.getField("PLUS_HANDLE");
            PLUS_FN_METHOD = VarUtil.class.getMethod("plusFn", Long.TYPE, Long.TYPE);
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    public static final Type.FnType PLUS_TYPE = fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE);

    public static final Var PLUS_VAR = new Var(PLUS_TYPE, PLUS_TYPE, PLUS_VALUE_FIELD, PLUS_FN_METHOD);

    public static final Env PLUS_ENV = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
}