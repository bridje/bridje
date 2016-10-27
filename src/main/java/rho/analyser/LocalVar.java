package rho.analyser;

import rho.runtime.Symbol;

public final class LocalVar {

    public final Symbol sym;

    public static LocalVar localVar(Symbol sym) {
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
