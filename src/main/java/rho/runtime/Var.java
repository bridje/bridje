package rho.runtime;

import rho.types.Type;

import java.lang.invoke.MethodType;

public class Var {
    public final Type type;
    public final BootstrapMethod bootstrapMethod;
    public final MethodType methodType;

    public static Var var(Type type, BootstrapMethod bootstrapMethod, MethodType methodType) {
        return new Var(type, bootstrapMethod, methodType);
    }

    private Var(Type type, BootstrapMethod bootstrapMethod, MethodType methodType) {
        this.type = type;
        this.bootstrapMethod = bootstrapMethod;
        this.methodType = methodType;
    }
}
