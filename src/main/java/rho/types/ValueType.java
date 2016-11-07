package rho.types;

import org.pcollections.Empty;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import org.pcollections.PVector;
import rho.Panic;

import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.stream.Collectors;

import static rho.Panic.panic;
import static rho.Util.*;

public abstract class ValueType extends Type implements ValueTypeHole {

    private ValueType() {
    }

    public PSet<TypeVar> ftvs() {
        return Empty.set();
    }

    public ValueType apply(TypeMapping mapping) {
        return this;
    }

    public final ValueType instantiate() {
        return apply(TypeMapping.from(ftvs().stream().collect(toPMap(ftv -> ftv, ftv -> new TypeVar()))));
    }

    private TypeMapping varBind(TypeVar var, ValueType t2) {
        if (t2.ftvs().contains(var)) {
            throw panic("Cyclical types: %s and %s", var, t2);
        } else {
            return TypeMapping.singleton(var, t2);
        }
    }

    public final TypeMapping unify(ValueType t2) {
        if (this == t2) {
            return TypeMapping.EMPTY;
        } else if (this instanceof TypeVar) {
            return varBind((TypeVar) this, t2);
        } else if (t2 instanceof TypeVar) {
            return varBind((TypeVar) t2, this);
        } else {
            return unify0(t2);
        }
    }

    final Panic cantUnify(ValueType t2) {
        return panic("Can't unify types %s and %s", this, t2);
    }

    TypeMapping unify0(ValueType t2) {
        throw cantUnify(t2);
    }

    public abstract Class<?> javaType();

    public static class SimpleType extends ValueType {
        public static final ValueType BOOL_TYPE = new SimpleType("Bool", Boolean.TYPE);
        public static final ValueType STRING_TYPE = new SimpleType("Str", String.class);
        public static final ValueType INT_TYPE = new SimpleType("Int", Long.TYPE);

        private final String name;
        private final Class<?> javaType;

        private SimpleType(String name, Class<?> javaType) {
            this.name = name;
            this.javaType = javaType;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public Class<?> javaType() {
            return javaType;
        }
    }

    public static final class VectorType extends ValueType {
        public final ValueType elemType;

        public static VectorType vectorType(ValueType elemType) {
            return new VectorType(elemType);
        }

        private VectorType(ValueType elemType) {
            this.elemType = elemType;
        }

        @Override
        public PSet<TypeVar> ftvs() {
            return elemType.ftvs();
        }

        @Override
        public ValueType apply(TypeMapping mapping) {
            return new VectorType(elemType.apply(mapping));
        }

        @Override
        TypeMapping unify0(ValueType t2) {
            if (t2 instanceof VectorType) {
                return elemType.unify(((VectorType) t2).elemType);
            } else {
                throw cantUnify(t2);
            }
        }

        @Override
        public Class<?> javaType() {
            return PVector.class;
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
    }

    public static final class SetType extends ValueType {
        public final ValueType elemType;

        public static SetType setType(ValueType elemType) {
            return new SetType(elemType);
        }

        private SetType(ValueType elemType) {
            this.elemType = elemType;
        }

        @Override
        public PSet<TypeVar> ftvs() {
            return elemType.ftvs();
        }

        @Override
        public ValueType apply(TypeMapping mapping) {
            return new SetType(elemType.apply(mapping));
        }

        @Override
        TypeMapping unify0(ValueType t2) {
            if (t2 instanceof SetType) {
                return elemType.unify(((SetType) t2).elemType);
            } else {
                throw cantUnify(t2);
            }
        }

        @Override
        public Class<?> javaType() {
            return PSet.class;
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
    }

    public static final class FnType extends ValueType {

        public final PVector<ValueType> paramTypes;
        public final ValueType returnType;

        public static FnType fnType(PVector<ValueType> paramTypes, ValueType returnType) {
            return new FnType(paramTypes, returnType);
        }

        private FnType(PVector<ValueType> paramTypes, ValueType returnType) {
            this.paramTypes = paramTypes;
            this.returnType = returnType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FnType fnType = (FnType) o;
            return Objects.equals(paramTypes, fnType.paramTypes) &&
                Objects.equals(returnType, fnType.returnType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(paramTypes, returnType);
        }

        @Override
        public String toString() {
            return String.format("(FnType (%s) %s)", paramTypes.stream().map(Object::toString).collect(Collectors.joining(", ")), returnType);
        }

        @Override
        public PSet<TypeVar> ftvs() {
            return paramTypes.stream()
                .flatMap(t -> t.ftvs().stream())
                .collect(toPSet())
                .plusAll(returnType.ftvs());
        }

        @Override
        public ValueType apply(TypeMapping mapping) {
            return new FnType(paramTypes.stream().map(t -> t.apply(mapping)).collect(toPVector()), returnType.apply(mapping));
        }

        @Override
        TypeMapping unify0(ValueType t2) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<?> javaType() {
            return MethodHandle.class;
        }
    }

    public static final class TypeVar extends ValueType {

        @Override
        public PSet<TypeVar> ftvs() {
            return HashTreePSet.singleton(this);
        }

        @Override
        public ValueType apply(TypeMapping mapping) {
            return mapping.mapping.getOrDefault(this, this);
        }

        @Override
        public Class<?> javaType() {
            return Object.class;
        }

        @Override
        public String toString() {
            return String.format("(TypeVar %s)", hashCode());
        }
    }
}
