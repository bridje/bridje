package rho.types;

import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.analyser.LocalVar;

final class LocalTypeEnv {
    final PMap<LocalVar, ValueType> env;
    static final LocalTypeEnv EMPTY_TYPE_ENV = localTypeEnv(Empty.map());

    static LocalTypeEnv localTypeEnv(PMap<LocalVar, ValueType> env) {
        return new LocalTypeEnv(env);
    }

    private LocalTypeEnv(PMap<LocalVar, ValueType> env) {
        this.env = env;
    }

    public LocalTypeEnv with(LocalVar localVar, ValueType bindingType) {
        return new LocalTypeEnv(env.plus(localVar, bindingType));
    }
}
