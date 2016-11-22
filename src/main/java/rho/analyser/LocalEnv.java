package rho.analyser;

import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.runtime.Symbol;

final class LocalEnv {

    public final PMap<Symbol, LocalVar> localVars;

    static final LocalEnv EMPTY_ENV = new LocalEnv(Empty.map());

    LocalEnv(PMap<Symbol, LocalVar> localVars) {
        this.localVars = localVars;
    }

    LocalEnv withLocal(Symbol symbol, LocalVar localVar) {
        return new LocalEnv(localVars.plus(symbol, localVar));
    }

}