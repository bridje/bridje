package rho.types;

import org.pcollections.Empty;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import rho.types.Type.TypeVar;

import java.util.Objects;

final class TypeMapping {
    final PMap<TypeVar, Type> mapping;

    static final TypeMapping EMPTY = new TypeMapping(Empty.map());

    public static TypeMapping singleton(TypeVar var, Type type) {
        return new TypeMapping(HashTreePMap.singleton(var, type));
    }

    public static TypeMapping from(PMap<TypeVar, Type> mapping) {
        return new TypeMapping(mapping);
    }

    TypeMapping(PMap<TypeVar, Type> mapping) {
        this.mapping = mapping;
    }

    TypeMapping with(PMap<TypeVar, Type> mapping) {
        return new TypeMapping(this.mapping.plusAll(mapping));
    }

    public TypeMapping with(TypeMapping mapping) {
        return with(mapping.mapping);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeMapping that = (TypeMapping) o;
        return Objects.equals(mapping, that.mapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapping);
    }

    @Override
    public String toString() {
        return String.format("(TypeMapping %s)", mapping);
    }
}
