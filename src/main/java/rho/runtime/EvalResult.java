package rho.runtime;

import rho.types.Type;

public class EvalResult {

    public final Env env;
    public final Type type;
    public final Object value;

    public EvalResult(Env env, Type type, Object value) {
        this.env = env;
        this.type = type;
        this.value = value;
    }
}
