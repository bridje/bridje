package rho.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static rho.Util.vectorOf;
import static rho.runtime.Var.var;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SimpleType.INT_TYPE;

public class VarUtil {

    public static CallSite bootstrapPlus(MethodHandles.Lookup lookup, String name, MethodType methodType) {
        try {
            return new ConstantCallSite(lookup.findStatic(VarUtil.class, "plusFn", MethodType.methodType(Long.TYPE, Long.TYPE, Long.TYPE)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static long plusFn(long a, long b) {
        return a + b;
    }

    public static final BootstrapMethod BOOTSTRAP_METHOD = new BootstrapMethod(VarUtil.class, "bootstrapPlus", MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

    public static final Var PLUS_VAR = var(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), BOOTSTRAP_METHOD, MethodType.methodType(Long.TYPE, Long.TYPE, Long.TYPE));

}