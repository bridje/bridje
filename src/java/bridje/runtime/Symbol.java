package bridje.runtime;

import java.util.Objects;

public class Symbol {
    public final String ns;
    public final String sym;

    public static Symbol symbol(String sym) {
        return new Symbol(null, sym);
    }

    public static Symbol symbol(String ns, String sym) {
        return new Symbol(ns, sym);
    }

    public Symbol(String ns, String sym) {
        this.ns = ns;
        this.sym = sym;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Symbol symbol = (Symbol) o;
        return Objects.equals(sym, symbol.sym);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ns, sym);
    }

    @Override
    public String toString() {
        return ns == null ? sym : String.format("%s/%s", ns, sym);
    }
}
