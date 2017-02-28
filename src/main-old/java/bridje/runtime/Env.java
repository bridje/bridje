package bridje.runtime;

import org.pcollections.Empty;
import org.pcollections.PMap;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static bridje.Util.or;
import static bridje.runtime.Symbol.symbol;

public class Env {

    public static final Env EMPTY = new Env(Empty.map());
    public static final Env CORE;

    static {
        CORE = EMPTY;
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

    public final PMap<NS, NSEnv> nsEnvs;

    public Env(PMap<NS, NSEnv> nsEnvs) {
        this.nsEnvs = nsEnvs;
    }

    public Env withNS(NS ns, NSEnv nsEnv) {
        return new Env(nsEnvs.plus(ns, nsEnv));
    }
}
