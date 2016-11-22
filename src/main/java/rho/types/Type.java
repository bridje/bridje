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
import static rho.types.Type.SimpleType.ENV_IO;

public abstract class Type {

    private Type() {
    }

    public PSet<TypeVar> ftvs() {
        return Empty.set();
    }

    public Type apply(TypeMapping mapping) {
        return this;
    }

    public final Type instantiate() {
        return apply(TypeMapping.from(ftvs().stream().collect(toPMap(ftv -> ftv, ftv -> new TypeVar()))));
    }

    private TypeMapping varBind(TypeVar var, Type t2) {
        if (t2.ftvs().contains(var)) {
            throw panic("Cyclical types: %s and %s", var, t2);
        } else {
            return TypeMapping.singleton(var, t2);
        }
    }

    public final TypeMapping unify(Type t2) {
        if (this == t2) {
            return TypeMapping.EMPTY;
        } else if (this == ENV_IO || t2 == ENV_IO) {
            throw new UnsupportedOperationException();
        } else if (this instanceof TypeVar) {
            return varBind((TypeVar) this, t2);
        } else if (t2 instanceof TypeVar) {
            return varBind((TypeVar) t2, this);
        } else {
            return unify0(t2);
        }
    }

    final Panic cantUnify(Type t2) {
        return panic("Can't unify types %s and %s", this, t2);
    }

    TypeMapping unify0(Type t2) {
        throw cantUnify(t2);
    }

    public abstract Class<?> javaType();

    public static class SimpleType extends Type {
        public static final Type BOOL_TYPE = new SimpleType("Bool", Boolean.TYPE);
        public static final Type STRING_TYPE = new SimpleType("Str", String.class);
        public static final Type INT_TYPE = new SimpleType("Int", Long.TYPE);
        public static final Type ENV_IO = new SimpleType("EnvIO", null);

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

    public static final class VectorType extends Type {
        public final Type elemType;

        public static VectorType vectorType(Type elemType) {
            return new VectorType(elemType);
        }

        private VectorType(Type elemType) {
            this.elemType = elemType;
        }

        @Override
        public PSet<TypeVar> ftvs() {
            return elemType.ftvs();
        }

        @Override
        public Type apply(TypeMapping mapping) {
            return new VectorType(elemType.apply(mapping));
        }

        @Override
        TypeMapping unify0(Type t2) {
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

    public static final class SetType extends Type {
        public final Type elemType;

        public static SetType setType(Type elemType) {
            return new SetType(elemType);
        }

        private SetType(Type elemType) {
            this.elemType = elemType;
        }

        @Override
        public PSet<TypeVar> ftvs() {
            return elemType.ftvs();
        }

        @Override
        public Type apply(TypeMapping mapping) {
            return new SetType(elemType.apply(mapping));
        }

        @Override
        TypeMapping unify0(Type t2) {
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

    public static final class FnType extends Type {

        public final PVector<Type> paramTypes;
        public final Type returnType;

        public static FnType fnType(PVector<Type> paramTypes, Type returnType) {
            return new FnType(paramTypes, returnType);
        }

        private FnType(PVector<Type> paramTypes, Type returnType) {
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
        public Type apply(TypeMapping mapping) {
            return new FnType(paramTypes.stream().map(t -> t.apply(mapping)).collect(toPVector()), returnType.apply(mapping));
        }

        @Override
        public Class<?> javaType() {
            return MethodHandle.class;
        }
    }

    public static final class TypeVar extends Type {

        @Override
        public PSet<TypeVar> ftvs() {
            return HashTreePSet.singleton(this);
        }

        @Override
        public Type apply(TypeMapping mapping) {
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
