package rho.runtime;

import rho.types.Type;

public class Var {
    public final Type type;
    public final BootstrapMethod bootstrapMethod;

    public static Var var(Type type, BootstrapMethod bootstrapMethod) {
        return new Var(type, bootstrapMethod);
    }

    private Var(Type type, BootstrapMethod bootstrapMethod) {
        this.type = type;
        this.bootstrapMethod = bootstrapMethod;
    }
}
