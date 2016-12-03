package bridje.runtime;

public class EvalResult {

    public final Env env;
    public final Object value;

    public EvalResult(Env env, Object value) {
        this.env = env;
        this.value = value;
    }
}
