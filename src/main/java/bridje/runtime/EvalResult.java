package bridje.runtime;

public class EvalResult<T> {

    public final Env env;
    public final T value;

    public EvalResult(Env env, T value) {
        this.env = env;
        this.value = value;
    }
}
