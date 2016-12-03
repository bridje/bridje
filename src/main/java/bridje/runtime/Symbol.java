package bridje.runtime;

import java.util.Objects;

public class Symbol {
    public final String sym;

    public static Symbol symbol(String sym) {
        return new Symbol(sym);
    }

    public Symbol(String sym) {
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
        return Objects.hash(sym);
    }

    @Override
    public String toString() {
        return sym;
    }
}
