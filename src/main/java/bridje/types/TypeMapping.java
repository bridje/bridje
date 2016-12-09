package bridje.types;

import bridje.analyser.Expr;
import bridje.types.Type.TypeVar;
import org.pcollections.Empty;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

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

    Expr<Type> applyTo(Expr<Type> expr) {
        return expr.fmapType(t -> t.apply(this));
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

    public TypeMapping with(TypeVar var, Type type) {
        return new TypeMapping(mapping.plus(var, type));
    }
}
