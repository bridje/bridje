package rho.runtime;

import org.pcollections.PSequence;
import org.pcollections.PVector;
import rho.types.Type;
import rho.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;
import static rho.util.Pair.zip;

public abstract class DataTypeConstructor<T> {
    public final T type;
    public final Symbol sym;

    DataTypeConstructor(T type, Symbol sym) {
        this.type = type;
        this.sym = sym;
    }

    public abstract <T_> DataTypeConstructor<T_> fmapType(Function<T, T_> fn);

    public abstract <U> U accept(ConstructorVisitor<? super T, U> visitor);

    public abstract boolean equals(DataTypeConstructor<T> that, Map<Type.TypeVar, Type.TypeVar> typeVarMapping);

    public static final class ValueConstructor<T> extends DataTypeConstructor<T> {

        public ValueConstructor(T type, Symbol sym) {
            super(type, sym);
        }

        @Override
        public <T_> DataTypeConstructor<T_> fmapType(Function<T, T_> fn) {
            return new ValueConstructor<T_>(fn.apply(type), sym);
        }

        @Override
        public <U> U accept(ConstructorVisitor<? super T, U> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(DataTypeConstructor<T> that, Map<Type.TypeVar, Type.TypeVar> typeVarMapping) {
            return this.equals(that);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValueConstructor that = (ValueConstructor) o;
            return Objects.equals(sym, that.sym);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym);
        }
    }

    public static final class VectorConstructor<T> extends DataTypeConstructor<T> {

        public final PVector<Type> paramTypes;

        public VectorConstructor(T type, Symbol sym, PVector<Type> paramTypes) {
            super(type, sym);
            this.paramTypes = paramTypes;
        }

        @Override
        public <T_> DataTypeConstructor<T_> fmapType(Function<T, T_> fn) {
            return new VectorConstructor<T_>(fn.apply(type), sym, paramTypes);
        }

        @Override
        public <U> U accept(ConstructorVisitor<? super T, U> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(DataTypeConstructor<T> o, Map<Type.TypeVar, Type.TypeVar> typeVarMapping) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VectorConstructor that = (VectorConstructor) o;

            if (!Objects.equals(sym, that.sym)
                || paramTypes.size() != that.paramTypes.size()) {
                return false;
            }

            // TODO why does this need a cast?
            PSequence<Pair<Type, Type>> paramTypePairs = zip(paramTypes, that.paramTypes);
            for (Pair<Type, Type> paramTypePair : paramTypePairs) {
                if (!paramTypePair.left.alphaEquivalentTo(paramTypePair.right, typeVarMapping)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VectorConstructor that = (VectorConstructor) o;

            return equals(that, new HashMap<>());
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym, paramTypes);
        }

        @Override
        public String toString() {
            return String.format("(VectorConstructor %s)", paramTypes.stream().map(Object::toString).collect(joining(" ")));
        }
    }


}
