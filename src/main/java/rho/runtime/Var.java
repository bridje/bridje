package rho.runtime;

import rho.types.Type;

public class Var {
    public final Type type;

    public static Var var(Type type) {
        return new Var(type);
    }

    private Var(Type type) {
        this.type = type;
    }
}
