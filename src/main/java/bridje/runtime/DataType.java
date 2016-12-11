package bridje.runtime;

import bridje.types.Type;
import bridje.util.Pair;
import org.pcollections.PVector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static bridje.Util.toPVector;
import static bridje.util.Pair.zip;
import static java.util.stream.Collectors.joining;

public class DataType<T> {
    public final T type;
    public final FQSymbol sym;
    public final PVector<Type.TypeVar> typeVars;
    public final PVector<DataTypeConstructor<T>> constructors;

    public DataType(T type, FQSymbol sym, PVector<Type.TypeVar> typeVars, PVector<DataTypeConstructor<T>> constructors) {
        this.type = type;
        this.sym = sym;
        this.typeVars = typeVars;
        this.constructors = constructors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataType<T> dataType = (DataType<T>) o;

        if (!Objects.equals(sym, dataType.sym)
            || typeVars.size() != dataType.typeVars.size()
            || constructors.size() != dataType.constructors.size()) {
            return false;
        }

        Map<Type.TypeVar, Type.TypeVar> typeVarMapping = new HashMap<>();

        for (Pair<Type.TypeVar, Type.TypeVar> typeVarPair : zip(typeVars, dataType.typeVars)) {
            if (!typeVarPair.left.alphaEquivalentTo(typeVarPair.right, typeVarMapping)) {
                return false;
            }
        }

        for (Pair<DataTypeConstructor<T>, DataTypeConstructor<T>> constructorPair : zip(constructors, dataType.constructors)) {
            if (!constructorPair.left.equals(constructorPair.right, new HashMap<>(typeVarMapping))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sym, constructors);
    }

    @Override
    public String toString() {
        return String.format("(DataType %s %s)",
            typeVars.isEmpty() ?
                sym :
                String.format("(%s %s)", sym, typeVars.stream().map(Object::toString).collect(joining(" '"))),
            constructors.stream().map(Object::toString).collect(joining(" ")));
    }

    public <T_> DataType<T_> fmapType(Function<T, T_> fn) {
        return new DataType<>(fn.apply(type), sym, typeVars, constructors.stream().map(c -> c.fmapType(fn)).collect(toPVector()));
    }

    public DataType<T> fmapParamTypes(Function<Type, Type> fn) {
        return new DataType<>(type, sym, typeVars, constructors.stream().map(c -> c.fmapParamTypes(fn)).collect(toPVector()));
    }
}
