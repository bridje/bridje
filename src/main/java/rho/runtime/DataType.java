package rho.runtime;

import org.pcollections.PVector;

import java.util.Objects;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;
import static rho.Util.toPVector;

public class DataType<T> {
    public final T type;
    public final Symbol sym;
    public final PVector<DataTypeConstructor<T>> constructors;

    public DataType(T type, Symbol sym, PVector<DataTypeConstructor<T>> constructors) {
        this.type = type;
        this.sym = sym;
        this.constructors = constructors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataType<T> dataType = (DataType<T>) o;
        return Objects.equals(sym, dataType.sym) &&
            Objects.equals(constructors, dataType.constructors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sym, constructors);
    }

    @Override
    public String toString() {
        return String.format("(DataType %s %s)", sym, constructors.stream().map(Object::toString).collect(joining(" ")));
    }

    public <T_> DataType<T_> fmapType(Function<T, T_> fn) {
        return new DataType<T_>(fn.apply(type), sym, constructors.stream().map(c -> c.fmapType(fn)).collect(toPVector()));
    }
}
