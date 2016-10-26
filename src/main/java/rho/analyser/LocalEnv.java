package rho.analyser;

import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.runtime.Symbol;

final class LocalEnv {

    static final class LocalVar {

        final Symbol sym;

        static LocalVar localVar(Symbol sym) {
            return new LocalVar(sym);
        }

        private LocalVar(Symbol sym) {
            this.sym = sym;
        }

        @Override
        public String toString() {
            return String.format("(LocalVar %s@%d", sym, hashCode());
        }
    }

    public final PMap<Symbol, LocalVar> localVars;

    static final LocalEnv EMPTY_ENV = new LocalEnv(Empty.map());

    LocalEnv(PMap<Symbol, LocalVar> localVars) {
        this.localVars = localVars;
    }

    LocalEnv withLocal(Symbol symbol, LocalVar localVar) {
        return new LocalEnv(localVars.plus(symbol, localVar));
    }

}
