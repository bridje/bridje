package rho.analyser;

import rho.runtime.Symbol;
import rho.types.ValueTypeHole;

public final class LocalVar<VT extends ValueTypeHole> {

    public final Symbol sym;
    public final VT type;

    public LocalVar(VT type, Symbol sym) {
        this.sym = sym;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("(LocalVar %s@%d)", sym, hashCode());
    }
}
