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
            return new ConstantCallSite(lookup.findStatic(VarUtil.class, "plusFn", MethodType.methodType(Long.class, Long.class, Long.class)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Long plusFn(Long a, Long b) {
        return a + b;
    }

    public static final BootstrapMethod BOOTSTRAP_METHOD = new BootstrapMethod(VarUtil.class, "bootstrapPlus", MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

    public static final Var PLUS_VAR = var(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), BOOTSTRAP_METHOD);

}