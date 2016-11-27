package rho.analyser;

import org.pcollections.Empty;
import org.pcollections.PCollection;
import org.pcollections.PMap;
import rho.runtime.Symbol;

import static rho.Util.toPMap;

final class LocalEnv {

    public final PMap<Symbol, LocalVar> localVars;

    static final LocalEnv EMPTY_ENV = new LocalEnv(Empty.map());

    LocalEnv(PMap<Symbol, LocalVar> localVars) {
        this.localVars = localVars;
    }

    LocalEnv withLocal(LocalVar localVar) {
        return new LocalEnv(this.localVars.plus(localVar.sym, localVar));
    }

    LocalEnv withLocals(PCollection<LocalVar> localVars) {
        return new LocalEnv(this.localVars.plusAll(localVars.stream().collect(toPMap(lv -> lv.sym, lv -> lv))));
    }
}
