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

    public static final Env EMPTY = new Env(Empty.map(), Empty.map(), Empty.map());
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
    public final PMap<FQSymbol, Var> vars;
    public final PMap<FQSymbol, DataType> dataTypes;

    public Env(PMap<NS, NSEnv> nsEnvs, PMap<FQSymbol, Var> vars, PMap<FQSymbol, DataType> dataTypes) {
        this.nsEnvs = nsEnvs;
        this.vars = vars;
        this.dataTypes = dataTypes;
    }

    private PMap<NS, NSEnv> updateNSEnv(NS ns, Function<NSEnv, NSEnv> fn) {
        return nsEnvs.plus(ns, fn.apply(Optional.ofNullable(nsEnvs.get(ns)).orElseGet(() -> NSEnv.empty(ns))));
    }

    public Env withVar(FQSymbol sym, Var var) {
        return new Env(updateNSEnv(sym.ns, nsEnv -> nsEnv.withVar(sym)), vars.plus(sym, var), dataTypes);
    }

    public Env withDataType(DataType dataType, PMap<FQSymbol, Var> constructorVars) {
        return new Env(
            updateNSEnv(dataType.sym.ns, nsEnv -> nsEnv.withDataType(dataType)),
            vars.plusAll(constructorVars), dataTypes.plus(dataType.sym, dataType));
    }

    public Env withNS(NS ns, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers, PMap<Symbol, Class<?>> imports) {
        return new Env(nsEnvs.plus(ns, NSEnv.fromDeclaration(ns, aliases, refers, imports)), vars, dataTypes);
    }

    private static <K, V> Optional<V> find(PMap<K, V> map, K key) {
        return Optional.ofNullable(map.get(key));
    }

    private static <K, V> Function<K, Optional<V>> find(PMap<K, V> map) {
        return key -> find(map, key);
    }

    private <T> Optional<T> resolveSym(NS ns, Symbol symbol, Function<FQSymbol, Optional<T>> fn) {
        return or(
            () -> find(nsEnvs, ns)
                .flatMap(nsEnv -> or(
                    () -> find(nsEnv.declarations, symbol).flatMap(fn),
                    () -> find(nsEnv.refers, symbol).flatMap(fn))),
            () -> find(nsEnvs, NS.CORE)
                .flatMap(nsEnv -> find(nsEnv.declarations, symbol).flatMap(fn)));
    }

    private <T> Optional<T> resolveQSym(NS ns, QSymbol qsym, Function<FQSymbol, Optional<T>> fn) {
        return Optional.ofNullable(nsEnvs.get(ns))
            .flatMap(nsEnv -> Optional.ofNullable(nsEnv.aliases.get(symbol(qsym.ns))))
            .flatMap(otherNS -> Optional.ofNullable(nsEnvs.get(otherNS)))
            .flatMap(otherNSEnv -> Optional.ofNullable(otherNSEnv.declarations.get(symbol(qsym.symbol))))
            .flatMap(fn);
    }

    public Optional<Var> resolveVar(NS ns, Symbol symbol) {
        return resolveSym(ns, symbol, find(vars));
    }

    public Optional<Var> resolveVar(NS ns, QSymbol qsym) {
        return resolveQSym(ns, qsym, find(vars));
    }

    public Optional<DataType> resolveDataType(NS ns, Symbol symbol) {
        return resolveSym(ns, symbol, find(dataTypes));
    }

    public Optional<DataType> resolveDataType(NS ns, QSymbol qsym) {
        return resolveQSym(ns, qsym, find(dataTypes));
    }

    public Optional<Class<?>> resolveImport(NS ns, Symbol symbol) {
        return or(
            () -> find(nsEnvs, ns)
                .flatMap(nsEnv -> find(nsEnv.imports, symbol)),
            () -> {
                try {
                    return Optional.of(Class.forName("java.lang." + symbol.sym));
                } catch (ClassNotFoundException e) {
                    return Optional.empty();
                }
            });
    }

    public Env require(NS ns) {
        System.out.println(getClass().getClassLoader().getResource(ns.name.replace('.', '/') + ".brj"));
        throw new UnsupportedOperationException();
    }
}
