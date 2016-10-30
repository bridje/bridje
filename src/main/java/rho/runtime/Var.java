package rho.runtime;

import rho.types.ValueType;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Var {

    public static final String BOOTSTRAP_METHOD_NAME = "__bootstrap";
    public static final MethodType BOOTSTRAP_METHOD_TYPE = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);

    public static final String VALUE_METHOD_NAME = "$$value";
    public static final String FN_METHOD_NAME = "$$invoke";

    public final ValueType type;
    private final IndyBootstrap indyBootstrap;
    public final MethodType functionMethodType;

    public static Var var(ValueType type, IndyBootstrap indyBootstrap, MethodType functionMethodType) {
        return new Var(type, indyBootstrap, functionMethodType);
    }

    private Var(ValueType type, IndyBootstrap indyBootstrap, MethodType functionMethodType) {
        this.type = type;
        this.indyBootstrap = indyBootstrap;
        this.functionMethodType = functionMethodType;
    }

    public Class<? extends IndyBootstrap> bootstrapClass() {
        return indyBootstrap.getClass();
    }
}
