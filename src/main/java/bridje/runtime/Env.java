package bridje.runtime;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static bridje.runtime.NS.KERNEL;

public class Env {
    public static final Env EMPTY = new Env();
    public static final Env CORE;

    static {
        CORE = Eval.loadNS(EMPTY, KERNEL).env;
    }

    private static volatile Env ENV = CORE;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("bridje-env-queue");
        return thread;
    });

    public static Env env() {
        return ENV;
    }

    public static EvalResult eval(Function<Env, EvalResult> fn) {
        try {
            return EXECUTOR.submit(() -> {
                EvalResult result = fn.apply(ENV);
                ENV = result.env;
                return result;
            }).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            } else {
                throw new RuntimeException(cause);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
