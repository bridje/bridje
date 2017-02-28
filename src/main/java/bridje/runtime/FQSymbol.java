package bridje.runtime;

import java.util.Objects;

public class FQSymbol {
    public final NS ns;
    public final Symbol symbol;

    public static FQSymbol fqSym(NS ns, Symbol sym) {
        return new FQSymbol(ns, sym);
    }

    private FQSymbol(NS ns, Symbol symbol) {
        this.ns = ns;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FQSymbol fqSymbol = (FQSymbol) o;
        return Objects.equals(ns, fqSymbol.ns) &&
            Objects.equals(symbol, fqSymbol.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ns, symbol);
    }

    @Override
    public String toString() {
        return String.format("%s/%s", ns.name, symbol.sym);
    }
}
