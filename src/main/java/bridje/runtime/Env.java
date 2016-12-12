package bridje.runtime;

import bridje.types.Type;
import org.pcollections.Empty;
import org.pcollections.PMap;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static bridje.runtime.Symbol.symbol;

public class Env {

    public static final Env EMPTY = new Env(Empty.map(), Empty.map(), Empty.map(), Empty.map());
    private static volatile Env ENV;

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
    public final PMap<FQSymbol, Var> vars;
    public final PMap<FQSymbol, DataType<Type>> dataTypes;
    public final PMap<FQSymbol, Class<?>> dataTypeSuperclasses;

    public Env(PMap<NS, NSEnv> nsEnvs, PMap<FQSymbol, Var> vars, PMap<FQSymbol, DataType<Type>> dataTypes, PMap<FQSymbol, Class<?>> dataTypeSuperclasses) {
        this.nsEnvs = nsEnvs;
        this.vars = vars;
        this.dataTypes = dataTypes;
        this.dataTypeSuperclasses = dataTypeSuperclasses;
    }

    private PMap<NS, NSEnv> updateNSEnv(NS ns, Function<NSEnv, NSEnv> fn) {
        return nsEnvs.plus(ns, fn.apply(Optional.ofNullable(nsEnvs.get(ns)).orElseGet(() -> NSEnv.empty(ns))));
    }

    public Env withVar(FQSymbol sym, Var var) {
        return new Env(updateNSEnv(sym.ns, nsEnv -> nsEnv.withVar(sym)), vars.plus(sym, var), dataTypes, dataTypeSuperclasses);
    }

    public Env withDataType(DataType<Type> dataType, Class<?> superclass, PMap<FQSymbol, Var> constructorVars) {
        return new Env(
            updateNSEnv(dataType.sym.ns, nsEnv -> nsEnv.withDataType(dataType)),
            vars.plusAll(constructorVars), dataTypes.plus(dataType.sym, dataType), dataTypeSuperclasses.plus(dataType.sym, superclass));
    }

    public Env withNS(NS ns, PMap<Symbol, NS> aliases) {
        return new Env(nsEnvs.plus(ns, NSEnv.fromDeclaration(ns, aliases)), vars, dataTypes, dataTypeSuperclasses);
    }

    public Optional<Var> resolveVar(NS ns, Symbol symbol) {
        return Optional.ofNullable(nsEnvs.get(ns))
            .flatMap(nsEnv -> Optional.ofNullable(nsEnv.vars.get(symbol)))
            .flatMap(fqSym -> Optional.ofNullable(vars.get(fqSym)));
    }

    public Optional<Var> resolveVar(NS ns, QSymbol qsym) {
        return Optional.ofNullable(nsEnvs.get(ns))
            .flatMap(nsEnv -> Optional.ofNullable(nsEnv.aliases.get(symbol(qsym.ns))))
            .flatMap(otherNS -> Optional.ofNullable(nsEnvs.get(otherNS)))
            .flatMap(otherNSEnv -> Optional.ofNullable(otherNSEnv.vars.get(symbol(qsym.symbol))))
            .flatMap(fqSym -> Optional.ofNullable(vars.get(fqSym)));
    }
}
