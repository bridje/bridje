package bridje.types;

import bridje.analyser.LocalVar;
import org.pcollections.Empty;
import org.pcollections.PMap;

final class LocalTypeEnv {
    final PMap<LocalVar, Type> env;
    static final LocalTypeEnv EMPTY_TYPE_ENV = localTypeEnv(Empty.map());

    static LocalTypeEnv localTypeEnv(PMap<LocalVar, Type> env) {
        return new LocalTypeEnv(env);
    }

    private LocalTypeEnv(PMap<LocalVar, Type> env) {
        this.env = env;
    }

    public LocalTypeEnv with(LocalVar localVar, Type bindingType) {
        return new LocalTypeEnv(env.plus(localVar, bindingType));
    }
}
