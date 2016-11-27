package rho.runtime;

import org.pcollections.PMap;
import rho.types.Type;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class Env {

    private static volatile Env ENV;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("rho-env-queue");
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

    public final PMap<Symbol, Var> vars;
    public final PMap<Symbol, DataType<Type>> dataTypes;
    public final PMap<Symbol, Class<?>> dataTypeSuperclasses;

    public Env(PMap<Symbol, Var> vars, PMap<Symbol, DataType<Type>> dataTypes, PMap<Symbol, Class<?>> dataTypeSuperclasses) {
        this.vars = vars;
        this.dataTypes = dataTypes;
        this.dataTypeSuperclasses = dataTypeSuperclasses;
    }

    public Env withVar(Symbol sym, Var var) {
        return new Env(vars.plus(sym, var), dataTypes, dataTypeSuperclasses);
    }

    public Env withDataType(DataType<Type> dataType, Class<?> superclass, PMap<Symbol, Var> constructorVars) {
        return new Env(vars.plusAll(constructorVars), dataTypes.plus(dataType.sym, dataType), dataTypeSuperclasses.plus(dataType.sym, superclass));
    }
}
