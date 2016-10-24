package rho.types;

import org.pcollections.Empty;
import org.pcollections.PMap;

import java.util.Objects;

import static rho.Panic.panic;

public abstract class Type {
    private Type() {
    }

    final PMap<TypeVar, Type> mgu(Type other) {
        if (this.getClass() == other.getClass() || this instanceof TypeVar) {
            return this.mgu0(other);
        } else if (other instanceof TypeVar) {
            return other.mgu0(this);
        } else {
            throw panic("Cannot unify types: %s and %s", this, other);
        }
    }

    abstract PMap<TypeVar, Type> mgu0(Type other);

    public static final Type STRING_TYPE = new Type() {
        @Override
        PMap<TypeVar, Type> mgu0(Type other) {
            return Empty.map();
        }

        @Override
        public String toString() {
            return "Str";
        }
    };

    public static final Type INT_TYPE = new Type() {
        @Override
        PMap<TypeVar, Type> mgu0(Type other) {
            return Empty.map();
        }

        @Override
        public String toString() {
            return "Int";
        }
    };

    public static final class VectorType extends Type {
        public final Type elemType;

        public VectorType(Type elemType) {
            this.elemType = elemType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VectorType that = (VectorType) o;
            return Objects.equals(elemType, that.elemType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elemType);
        }

        @Override
        public String toString() {
            return String.format("[%s]", elemType);
        }

        @Override
        PMap<TypeVar, Type> mgu0(Type other) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class SetType extends Type {
        public final Type elemType;

        public SetType(Type elemType) {
            this.elemType = elemType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SetType setType = (SetType) o;
            return Objects.equals(elemType, setType.elemType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elemType);
        }

        @Override
        public String toString() {
            return String.format("^[%s]", elemType);
        }

        @Override
        PMap<TypeVar, Type> mgu0(Type other) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class TypeVar extends Type {

        @Override
        public String toString() {
            return String.format("(TypeVar %s)", hashCode());
        }

        @Override
        PMap<TypeVar, Type> mgu0(Type other) {
            throw new UnsupportedOperationException();
        }
    }
}
