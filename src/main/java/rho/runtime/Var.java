package rho.runtime;

import rho.types.Type;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Var {

    public static final String BOOTSTRAP_METHOD_NAME = "__bootstrap";
    public static final MethodType BOOTSTRAP_METHOD_TYPE = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);

    public static final String VALUE_METHOD_NAME = "$$value";
    public static final String FN_METHOD_NAME = "$$invoke";

    public final Type declaredType;
    public final Type inferredType;

    private final IndyBootstrap indyBootstrap;
    public final MethodType functionMethodType;

    public static Var var(Type declaredType, Type inferredType, IndyBootstrap indyBootstrap, MethodType functionMethodType) {
        return new Var(declaredType, inferredType, indyBootstrap, functionMethodType);
    }

    private Var(Type declaredType, Type inferredType, IndyBootstrap indyBootstrap, MethodType functionMethodType) {
        this.declaredType = declaredType;
        this.inferredType = inferredType;
        this.indyBootstrap = indyBootstrap;
        this.functionMethodType = functionMethodType;
    }

    public Type visibleType() {
        if (declaredType != null) {
            return declaredType;
        } else {
            return inferredType;
        }
    }

    public Class<? extends IndyBootstrap> bootstrapClass() {
        return indyBootstrap.getClass();
    }
}
