package rho.analyser;

import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.runtime.Symbol;
import rho.types.Type;

import java.util.Optional;

class LocalTypeEnv {
    public static final LocalTypeEnv EMPTY = new LocalTypeEnv(Empty.map());

    private final PMap<Symbol, Type.DataTypeType> localEnv;

    LocalTypeEnv(PMap<Symbol, Type.DataTypeType> localEnv) {
        this.localEnv = localEnv;
    }

    Optional<Type.DataTypeType> resolve(Symbol symbol) {
        return Optional.ofNullable(localEnv.get(symbol));
    }
}
