package bridje.compiler;

import bridje.Util;
import bridje.runtime.*;
import bridje.types.Type.*;
import bridje.types.TypeVisitor;
import bridje.util.Pair;
import org.objectweb.asm.*;
import org.pcollections.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

import static bridje.Util.*;
import static bridje.compiler.ClassLike.fromClass;
import static bridje.compiler.Instructions.FieldOp.GET_STATIC;
import static bridje.compiler.Instructions.MethodInvoke.*;
import static org.objectweb.asm.Opcodes.*;

interface Instructions {

    void apply(MethodVisitor mv);

    Instructions MZERO = (mv) -> {
    };

    static Instructions mplus(Iterable<Instructions> instructions) {
        return mv -> {
            for (Instructions instruction : instructions) {
                instruction.apply(mv);
            }
        };
    }

    static Instructions mplus(Instructions... instructions) {
        return mplus(Arrays.asList(instructions));
    }

    static Instructions loadObject(Object obj) {
        return mv -> mv.visitLdcInsn(obj);
    }

    static Instructions loadSymbol(Symbol sym) {
        return newObject(fromClass(Symbol.class), Util.vectorOf(String.class), loadObject(sym.sym));
    }

    static Instructions withTypeLocals(Locals locals, PSet<TypeVar> typeVars, Function<Locals, Instructions> fn) {
        Locals typeLocals = locals.split();
        Instructions loadTypeVars = MZERO;
        for (TypeVar typeVar : typeVars) {
            typeLocals = typeLocals.withVarLocal(typeVar, TypeVar.class);
            loadTypeVars = mplus(loadTypeVars, typeLocals.get(typeVar)
                .store(newObject(fromClass(TypeVar.class), vectorOf(), MZERO)));
        }

        return mplus(loadTypeVars, fn.apply(typeLocals), typeLocals.clear());
    }

    static Instructions loadType(bridje.types.Type type, Locals locals) {
        return type.accept(new TypeVisitor<Instructions>() {
            @Override
            public Instructions visitBool() {
                return fieldOp(GET_STATIC, fromClass(SimpleType.class), "BOOL_TYPE", fromClass(bridje.types.Type.class));
            }

            @Override
            public Instructions visitString() {
                return fieldOp(GET_STATIC, fromClass(SimpleType.class), "STRING_TYPE", fromClass(bridje.types.Type.class));
            }

            @Override
            public Instructions visitLong() {
                return fieldOp(GET_STATIC, fromClass(SimpleType.class), "INT_TYPE", fromClass(bridje.types.Type.class));
            }

            @Override
            public Instructions visitEnvIO() {
                return fieldOp(GET_STATIC, fromClass(SimpleType.class), "ENV_IO", fromClass(bridje.types.Type.class));
            }

            @Override
            public Instructions visit(VectorType type) {
                return newObject(fromClass(VectorType.class), Util.vectorOf(bridje.types.Type.class), type.elemType.accept(this));
            }

            @Override
            public Instructions visit(SetType type) {
                return newObject(fromClass(SetType.class), Util.vectorOf(bridje.types.Type.class), type.elemType.accept(this));
            }

            @Override
            public Instructions visit(MapType type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(FnType type) {
                return newObject(fromClass(FnType.class), Util.vectorOf(PVector.class, bridje.types.Type.class),
                    mplus(loadVector(Type.class, type.paramTypes.stream()
                            .map(t -> t.accept(this)).collect(toPVector())),
                        type.returnType.accept(this)));
            }

            @Override
            public Instructions visit(TypeVar type) {
                return locals.get(type).load();
            }

            @Override
            public Instructions visit(DataTypeType type) {
                return newObject(fromClass(DataTypeType.class), vectorOf(Symbol.class, Class.class),
                    mplus(
                        loadSymbol(type.name),
                        type.javaType == null ? loadNull() : loadClass(type.javaType)
                    ));
            }

            @Override
            public Instructions visit(bridje.types.Type.AppliedType type) {
                return newObject(fromClass(bridje.types.Type.AppliedType.class), Util.vectorOf(bridje.types.Type.class, PVector.class),
                    mplus(type.appliedType.accept(this),
                        loadVector(Type.class, type.typeParams.stream()
                            .map(t -> t.accept(this)).collect(toPVector()))));
            }
        });
    }

    static Instructions loadDataType(DataType<? extends bridje.types.Type> dataType, Locals locals) {
        return withTypeLocals(locals,
            dataType.constructors.stream()
                .flatMap(c -> c.typeVars().stream())
                .collect(toPSet()).plusAll(dataType.typeVars),

            typeLocals -> newObject(fromClass(DataType.class), vectorOf(Object.class, Symbol.class, PVector.class, PVector.class), mplus(
                loadType(dataType.type, typeLocals),
                loadSymbol(dataType.sym),
                loadVector(TypeVar.class, dataType.typeVars.stream().map(tv -> loadType(tv, typeLocals)).collect(toPVector())),
                loadVector(DataTypeConstructor.class, dataType.constructors.stream()
                    .map(c -> c.accept(new ConstructorVisitor<bridje.types.Type, Instructions>() {
                        @Override
                        public Instructions visit(DataTypeConstructor.ValueConstructor<? extends bridje.types.Type> constructor) {
                            return newObject(fromClass(DataTypeConstructor.ValueConstructor.class), vectorOf(Object.class, Symbol.class),
                                mplus(
                                    loadType(constructor.type, typeLocals),
                                    loadSymbol(c.sym)));
                        }

                        @Override
                        public Instructions visit(DataTypeConstructor.VectorConstructor<? extends bridje.types.Type> constructor) {
                            return newObject(fromClass(DataTypeConstructor.VectorConstructor.class), vectorOf(Object.class, Symbol.class, PVector.class),
                                mplus(
                                    loadType(constructor.type, typeLocals),
                                    loadSymbol(c.sym),
                                    loadVector(bridje.types.Type.class, constructor.paramTypes.stream().map(pt -> loadType(pt, typeLocals)).collect(toPVector()))));
                        }
                    }))

                    .collect(toPVector()))
            )));
    }

    static Instructions loadClass(Class clazz) {
        Type type = Type.getType(clazz);
        switch (type.getSort()) {
            case Type.LONG:
                return fieldOp(GET_STATIC, fromClass(Long.class), "TYPE", fromClass(Class.class));

            case Type.OBJECT:
                return mv -> mv.visitLdcInsn(type);
            default:
                throw new UnsupportedOperationException();
        }
    }

    static Instructions loadNull() {
        return mv -> mv.visitInsn(ACONST_NULL);
    }

    enum MethodInvoke {
        INVOKE_STATIC(Opcodes.INVOKESTATIC, false), INVOKE_VIRTUAL(INVOKEVIRTUAL, false), INVOKE_SPECIAL(INVOKESPECIAL, false), INVOKE_INTERFACE(INVOKEINTERFACE, true);

        final int opcode;
        final boolean isInterface;

        MethodInvoke(int opcode, boolean isInterface) {
            this.opcode = opcode;
            this.isInterface = isInterface;
        }
    }

    static Instructions methodCall(ClassLike classLike, MethodInvoke methodInvoke, String name, Class<?> returnType, PVector<Class<?>> paramTypes) {
        if (returnType == null || paramTypes.stream().anyMatch(pt -> pt == null)) {
            throw new UnsupportedOperationException();
        }
        return mv -> mv.visitMethodInsn(methodInvoke.opcode, classLike.getInternalName(), name,
            Type.getMethodDescriptor(Type.getType(returnType), paramTypes.stream().map(Type::getType).toArray(Type[]::new)),
            methodInvoke.isInterface);
    }

    Instructions OBJECT_SUPER_CONSTRUCTOR_CALL = methodCall(fromClass(Object.class), MethodInvoke.INVOKE_SPECIAL, "<init>", Void.TYPE, Empty.vector());

    static Instructions loadThis() {
        return mv -> mv.visitVarInsn(ALOAD, 0);
    }

    static Instructions newObject(ClassLike classLike, PVector<Class<?>> params, Instructions paramInstructions) {
        return
            mplus(
                mv -> {
                    mv.visitTypeInsn(NEW, classLike.getInternalName());
                    mv.visitInsn(DUP);
                },
                paramInstructions,

                methodCall(classLike, INVOKE_SPECIAL, "<init>", Void.TYPE, params));
    }

    static Instructions box(Class<?> clazz) {
        switch (Type.getType(clazz).getSort()) {
            case Type.OBJECT:
                return MZERO;
            case Type.LONG:
                return methodCall(fromClass(Long.class), INVOKE_STATIC, "valueOf", Long.class, Util.vectorOf(Long.TYPE));
            case Type.BOOLEAN:
                return methodCall(fromClass(Boolean.class), INVOKE_STATIC, "valueOf", Boolean.class, Util.vectorOf(Boolean.TYPE));
        }

        throw new UnsupportedOperationException();
    }

    static Instructions arrayOf(Class<?> clazz, PVector<Instructions> instructions) {
        return mv -> {
            mv.visitLdcInsn(instructions.size());
            mv.visitTypeInsn(ANEWARRAY, Type.getType(Object.class).getInternalName());

            for (int i = 0; i < instructions.size(); i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                instructions.get(i).apply(mv);
                box(clazz).apply(mv);
                mv.visitInsn(AASTORE);
            }
        };
    }

    Instructions ARRAY_AS_LIST = methodCall(fromClass(Arrays.class), INVOKE_STATIC, "asList", List.class, Util.vectorOf(Object[].class));

    static Instructions loadVector(Class<?> clazz, PVector<Instructions> instructions) {
        return mplus(
            arrayOf(clazz, instructions),
            ARRAY_AS_LIST,
            methodCall(fromClass(TreePVector.class), INVOKE_STATIC, "from", TreePVector.class, Util.vectorOf(Collection.class)));
    }

    static Instructions loadSet(Class<?> clazz, PVector<Instructions> instructions) {
        return mplus(
            arrayOf(clazz, instructions),
            ARRAY_AS_LIST,
            methodCall(fromClass(HashTreePSet.class), INVOKE_STATIC, "from", MapPSet.class, Util.vectorOf(Collection.class)));
    }

    static Instructions loadMap(Class<?> keyType, Class<?> valueType, PVector<Pair<Instructions, Instructions>> instructions) {
        Instructions boxKey = box(keyType);
        Instructions boxValue = box(valueType);
        return mplus(
            newObject(fromClass(HashMap.class), Empty.vector(), MZERO),
            mplus(instructions.stream()
                .map(i ->
                    mplus(
                        mv -> mv.visitInsn(DUP),
                        i.left, boxKey,
                        i.right, boxValue,
                        methodCall(fromClass(Map.class), INVOKE_INTERFACE, "put", Object.class, vectorOf(Object.class, Object.class)),
                        mv -> mv.visitInsn(POP)))
                .collect(toPVector())),
            methodCall(fromClass(HashTreePMap.class), INVOKE_STATIC, "from", HashPMap.class, vectorOf(Map.class))
        );
    }

    static Instructions loadBool(boolean value) {
        return mv -> mv.visitInsn(value ? ICONST_1 : ICONST_0);
    }

    static Instructions varInvoke(Var var) {
        Method method = var.fnMethod;
        return methodCall(fromClass(method.getDeclaringClass()), INVOKE_STATIC, method.getName(), method.getReturnType(), TreePVector.from(Arrays.asList(method.getParameterTypes())));
    }

    static Instructions varCall(Var var, PVector<Instructions> paramInstructions) {
        return mplus(
            mplus(paramInstructions),
            varInvoke(var));
    }

    static Instructions ret(Class<?> clazz) {
        Type type = Type.getType(clazz);
        return mv -> mv.visitInsn(type.getOpcode(IRETURN));
    }

    static Instructions ifCall(Instructions testInstructions, Instructions thenInstructions, Instructions elseInstructions) {
        Label elseLabel = new Label();
        Label endLabel = new Label();
        return mplus(
            testInstructions,
            mv -> mv.visitJumpInsn(IFEQ, elseLabel),
            thenInstructions,
            mv -> {
                mv.visitJumpInsn(GOTO, endLabel);
                mv.visitLabel(elseLabel);
            },
            elseInstructions,
            mv -> mv.visitLabel(endLabel));
    }

    static Instructions globalVarValue(Var var) {
        Field valueField = var.valueField;
        return fieldOp(GET_STATIC, fromClass(valueField.getDeclaringClass()), valueField.getName(), fromClass(valueField.getType()));
    }

    enum FieldOp {
        PUT_STATIC(PUTSTATIC), GET_STATIC(GETSTATIC), GET_FIELD(GETFIELD), PUT_FIELD(PUTFIELD);

        final int opcode;

        FieldOp(int opcode) {
            this.opcode = opcode;
        }
    }

    static Instructions fieldOp(FieldOp op, ClassLike owner, String fieldName, ClassLike fieldType) {
        return mv -> {
            mv.visitFieldInsn(op.opcode, owner.getInternalName(), fieldName, fieldType.getDescriptor());
        };
    }

    static Instructions loadMethodType(MethodType methodType) {
        return loadObject(Type.getMethodType(methodType.toMethodDescriptorString()));
    }

    static Instructions staticMethodHandle(ClassLike classLike, String methodName, PVector<Class<?>> paramClasses, Class<?> returnClass) {
        return loadObject(new Handle(H_INVOKESTATIC, classLike.getInternalName(), methodName,
            MethodType.methodType(returnClass, paramClasses.toArray(new Class<?>[paramClasses.size()])).toMethodDescriptorString(), false));
    }

    static Instructions virtualMethodHandle(Class<?> clazz, String methodName, PVector<Class<?>> paramClasses, Class<?> returnClass) {
        return loadObject(new Handle(H_INVOKEVIRTUAL, Type.getType(clazz).getInternalName(), methodName,
            MethodType.methodType(returnClass, paramClasses.toArray(new Class<?>[paramClasses.size()])).toMethodDescriptorString(), false));
    }

    static Instructions bindMethodHandle() {
        return methodCall(fromClass(MethodHandle.class), INVOKE_VIRTUAL, "bindTo", MethodHandle.class, Util.vectorOf(Object.class));
    }
}
