package rho.runtime;

import org.pcollections.HashTreePMap;
import rho.types.ValueType;

import java.lang.invoke.*;

import static rho.Util.vectorOf;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.Var.*;
import static rho.types.ValueType.FnType.fnType;
import static rho.types.ValueType.SimpleType.INT_TYPE;

public class VarUtil implements IndyBootstrap {

    public static CallSite __bootstrap(MethodHandles.Lookup lookup, String name, MethodType methodType) {
        MethodHandle plusFn;

        try {
            plusFn = lookup.findStatic(VarUtil.class, "plusFn", MethodType.methodType(Long.TYPE, Long.TYPE, Long.TYPE));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        switch (name) {
            case VALUE_METHOD_NAME:
                return new ConstantCallSite(MethodHandles.constant(MethodHandle.class, plusFn));
            case FN_METHOD_NAME:
                return new ConstantCallSite(plusFn);
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static long plusFn(long a, long b) {
        return a + b;
    }

    public static final ValueType.FnType PLUS_TYPE = fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE);

    public static final Var PLUS_VAR = var(PLUS_TYPE, new VarUtil(), MethodType.methodType(Long.TYPE, Long.TYPE, Long.TYPE));

    public static final Env PLUS_ENV = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));

    @Override
    public void setHandles(MethodHandle valueHandle, MethodHandle fnHandle) {
        throw new UnsupportedOperationException();
    }
}