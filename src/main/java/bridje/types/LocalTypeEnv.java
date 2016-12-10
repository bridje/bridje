package bridje.types;

import bridje.analyser.LocalVar;
import org.pcollections.Empty;
import org.pcollections.PMap;

import static bridje.Util.toPMap;
import static bridje.Util.toPSet;

final class LocalTypeEnv {

    static class LocalTypeEnvValue {
        final MonomorphicEnv monomorphicEnv;
        final Type type;

        LocalTypeEnvValue(MonomorphicEnv monomorphicEnv, Type type) {
            this.monomorphicEnv = monomorphicEnv;
            this.type = type;
        }

        LocalTypeEnvValue instantiate() {
            TypeMapping mapping = new TypeMapping(monomorphicEnv.typings.values().stream().flatMap(t -> t.ftvs().stream()).collect(toPSet())
                .plusAll(type.ftvs())
                .stream().collect(toPMap(tv -> tv, tv -> new Type.TypeVar())));

            return new LocalTypeEnvValue(monomorphicEnv.apply(mapping), type.apply(mapping));
        }
    }

    final PMap<LocalVar, LocalTypeEnvValue> env;
    static final LocalTypeEnv EMPTY_TYPE_ENV = localTypeEnv(Empty.map());

    static LocalTypeEnv localTypeEnv(PMap<LocalVar, LocalTypeEnvValue> env) {
        return new LocalTypeEnv(env);
    }

    private LocalTypeEnv(PMap<LocalVar, LocalTypeEnvValue> env) {
        this.env = env;
    }

    public LocalTypeEnv with(LocalVar localVar, LocalTypeEnvValue localTypeEnvValue) {
        return new LocalTypeEnv(env.plus(localVar, localTypeEnvValue));
    }
}
