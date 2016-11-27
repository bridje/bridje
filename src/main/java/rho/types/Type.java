package rho.types;

import org.pcollections.Empty;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import org.pcollections.PVector;
import rho.Panic;
import rho.compiler.EnvUpdate;
import rho.runtime.Symbol;
import rho.util.Pair;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static rho.Panic.panic;
import static rho.Util.*;
import static rho.types.Type.SimpleType.ENV_IO;
import static rho.util.Pair.zip;

public abstract class Type {

    public final Class<?> javaType;

    Type(Class<?> javaType) {
        this.javaType = javaType;
    }

    public abstract <T> T accept(TypeVisitor<T> visitor);

    public PSet<TypeVar> ftvs() {
        return Empty.set();
    }

    public Type apply(TypeMapping mapping) {
        return this;
    }

    public final Type instantiate() {
        return apply(TypeMapping.from(ftvs().stream().collect(toPMap(ftv -> ftv, ftv -> new TypeVar()))));
    }

    public final Type instantiateAll() {
        return apply(TypeMapping.from(typeVars().stream().collect(toPMap(tv -> tv, tv -> new TypeVar()))));
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



    public abstract PSet<TypeVar> typeVars();

    abstract boolean alphaEquivalentTo0(Type t2, Map<TypeVar, TypeVar> mapping);

    public final boolean alphaEquivalentTo(Type t2) {
        return this.instantiateAll().alphaEquivalentTo0(t2.instantiateAll(), new HashMap<>());
    }

    public final boolean subtypeOf(Type type) {
        return alphaEquivalentTo(type.apply(this.unify(type)));
    }

    public static abstract class SimpleType extends Type {
        public static final Type BOOL_TYPE = new SimpleType("Bool", Boolean.TYPE) {
            @Override
            public <T> T accept(TypeVisitor<T> visitor) {
                return visitor.visitBool();
            }
        };
        public static final Type STRING_TYPE = new SimpleType("Str", String.class) {
            @Override
            public <T> T accept(TypeVisitor<T> visitor) {
                return visitor.visitString();
            }
        };
        public static final Type INT_TYPE = new SimpleType("Int", Long.TYPE) {
            @Override
            public <T> T accept(TypeVisitor<T> visitor) {
                return visitor.visitLong();
            }
        };
        public static final Type ENV_IO = new SimpleType("EnvIO", EnvUpdate.class) {
            @Override
            public <T> T accept(TypeVisitor<T> visitor) {
                return visitor.visitEnvIO();
            }
        };

        private final String name;

        private SimpleType(String name, Class<?> javaType) {
            super(javaType);
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public PSet<TypeVar> typeVars() {
            return Empty.set();
        }

        @Override
        final boolean alphaEquivalentTo0(Type t2, Map<TypeVar, TypeVar> mapping) {
            return this == t2;
        }
    }

    public static final class VectorType extends Type {
        public final Type elemType;

        public static VectorType vectorType(Type elemType) {
            return new VectorType(elemType);
        }

        private VectorType(Type elemType) {
            super(PVector.class);
            this.elemType = elemType;
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
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
        public PSet<TypeVar> typeVars() {
            return elemType.typeVars();
        }

        @Override
        boolean alphaEquivalentTo0(Type t2, Map<TypeVar, TypeVar> mapping) {
            return t2 instanceof VectorType && elemType.alphaEquivalentTo0(((VectorType) t2).elemType, mapping);
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
            super(PSet.class);
            this.elemType = elemType;
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
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
        public PSet<TypeVar> typeVars() {
            return elemType.typeVars();
        }

        @Override
        boolean alphaEquivalentTo0(Type t2, Map<TypeVar, TypeVar> mapping) {
            return t2 instanceof SetType && elemType.alphaEquivalentTo0(((SetType) t2).elemType, mapping);
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

        public FnType(PVector<Type> paramTypes, Type returnType) {
            super(MethodHandle.class);
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
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
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
        public PSet<TypeVar> typeVars() {
            return paramTypes.stream().flatMap(pt -> pt.typeVars().stream()).collect(toPSet()).plusAll(returnType.typeVars());
        }

        @Override
        TypeMapping unify0(Type t2) {
            if (t2 instanceof FnType) {
                TypeMapping mapping = returnType.unify(((FnType) t2).returnType);
                PVector<Type> t2ParamTypes = ((FnType) t2).paramTypes;
                if (this.paramTypes.size() == t2ParamTypes.size()) {
                    for (Pair<Type, Type> types : zip(this.paramTypes, t2ParamTypes)) {
                        mapping = types.left.apply(mapping).unify(types.right.apply(mapping));
                    }

                    return mapping;
                }
            }

            throw cantUnify(t2);
        }

        @Override
        boolean alphaEquivalentTo0(Type t2, Map<TypeVar, TypeVar> mapping) {
            if (!(t2 instanceof FnType)) {
                return false;
            }

            FnType t2Fn = (FnType) t2;

            if (!returnType.alphaEquivalentTo0(t2Fn.returnType, mapping)) {
                return false;
            } else {
                PVector<Type> t2Params = t2Fn.paramTypes;
                if (t2Params.size() != paramTypes.size()) {
                    return false;
                }

                for (Pair<Type, Type> pts : zip(paramTypes, t2Params)) {
                    if (!pts.left.alphaEquivalentTo0(pts.right, mapping)) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    public static final class TypeVar extends Type {

        public TypeVar() {
            super(Object.class);
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public PSet<TypeVar> ftvs() {
            return HashTreePSet.singleton(this);
        }

        @Override
        public Type apply(TypeMapping mapping) {
            return mapping.mapping.getOrDefault(this, this);
        }

        @Override
        public PSet<TypeVar> typeVars() {
            return HashTreePSet.singleton(this);
        }

        @Override
        boolean alphaEquivalentTo0(Type t2, Map<TypeVar, TypeVar> mapping) {
            if (!(t2 instanceof TypeVar)) {
                return false;
            }

            TypeVar tvar2 = (TypeVar) t2;

            TypeVar t1Mapped = mapping.get(this);
            TypeVar t2Mapped = mapping.get(t2);

            if (t2Mapped == this && t1Mapped == tvar2) {
                return true;
            } else if (t2Mapped == null && t1Mapped == null) {
                mapping.put(this, tvar2);
                mapping.put(tvar2, this);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("(TypeVar %s)", hashCode());
        }
    }

    public static final class DataTypeType extends Type {

        public final Symbol name;

        public DataTypeType(Symbol name, Class<?> javaType) {
            super(javaType);
            this.name = name;
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public PSet<TypeVar> ftvs() {
            return Empty.set();
        }

        @Override
        public PSet<TypeVar> typeVars() {
            return Empty.set();
        }

        @Override
        boolean alphaEquivalentTo0(Type t2, Map<TypeVar, TypeVar> mapping) {
            return this == t2 || (t2 instanceof DataTypeType && Objects.equals(name, ((DataTypeType) t2).name));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DataTypeType that = (DataTypeType) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
