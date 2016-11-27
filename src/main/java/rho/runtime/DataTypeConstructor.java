package rho.runtime;

import rho.types.Type;

import java.util.Objects;
import java.util.function.Function;

public class DataTypeConstructor<T> {
    public final T type;
    public final Symbol sym;

    public DataTypeConstructor(T type, Symbol sym) {
        this.type = type;
        this.sym = sym;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataTypeConstructor that = (DataTypeConstructor) o;
        return Objects.equals(sym, that.sym);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sym);
    }

    @Override
    public String toString() {
        return sym.toString();
    }

    public <T_> DataTypeConstructor<T_> fmapType(Function<T, T_> fn) {
        return new DataTypeConstructor<T_>(fn.apply(type), sym);
    }
}
