package bridje.analyser;

import bridje.runtime.Symbol;

public final class LocalVar {

    public final Symbol sym;

    public LocalVar(Symbol sym) {
        this.sym = sym;
    }

    @Override
    public String toString() {
        return String.format("(LocalVar %s@%d)", sym, hashCode());
    }
}
