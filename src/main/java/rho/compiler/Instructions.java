package rho.compiler;

import org.objectweb.asm.*;
import org.pcollections.*;
import rho.Util;
import rho.runtime.*;
import rho.types.Type.SetType;
import rho.types.Type.SimpleType;
import rho.types.Type.VectorType;
import rho.types.TypeVisitor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;
import static rho.Util.toPVector;
import static rho.Util.vectorOf;
import static rho.compiler.ClassLike.fromClass;
import static rho.compiler.Instructions.FieldOp.GET_FIELD;
import static rho.compiler.Instructions.FieldOp.GET_STATIC;
import static rho.compiler.Instructions.MethodInvoke.*;

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

    static Instructions loadType(rho.types.Type type, Locals locals) {
        int nextIdx = locals.nextIdx;

        Map<rho.types.Type.TypeVar, Integer> typeVars_ = new HashMap<>();
        for (rho.types.Type.TypeVar typeVar : type.typeVars()) {
            typeVars_.put(typeVar, nextIdx++);
        }

        PMap<rho.types.Type.TypeVar, Integer> typeVars = HashTreePMap.from(typeVars_);

        return mplus(
            mplus(
                typeVars.entrySet().stream()
                    .map(e ->
                        mplus(
                            newObject(fromClass(rho.types.Type.TypeVar.class), Util.vectorOf(), MZERO),
                            mv -> mv.visitVarInsn(ASTORE, e.getValue())))
                    .collect(toPVector())),

            type.accept(new TypeVisitor<Instructions>() {
                @Override
                public Instructions visitBool() {
                    return fieldOp(GET_STATIC, fromClass(SimpleType.class), "BOOL_TYPE", fromClass(rho.types.Type.class));
                }

                @Override
                public Instructions visitString() {
                    return fieldOp(GET_STATIC, fromClass(SimpleType.class), "STRING_TYPE", fromClass(rho.types.Type.class));
                }

                @Override
                public Instructions visitLong() {
                    return fieldOp(GET_STATIC, fromClass(SimpleType.class), "INT_TYPE", fromClass(rho.types.Type.class));
                }

                @Override
                public Instructions visitEnvIO() {
                    return fieldOp(GET_STATIC, fromClass(SimpleType.class), "ENV_IO", fromClass(rho.types.Type.class));
                }

                @Override
                public Instructions visit(VectorType type) {
                    return newObject(fromClass(VectorType.class), Util.vectorOf(rho.types.Type.class), type.elemType.accept(this));
                }

                @Override
                public Instructions visit(SetType type) {
                    return newObject(fromClass(SetType.class), Util.vectorOf(rho.types.Type.class), type.elemType.accept(this));
                }

                @Override
                public Instructions visit(rho.types.Type.FnType type) {
                    return newObject(fromClass(rho.types.Type.FnType.class), Util.vectorOf(PVector.class, rho.types.Type.class),
                        mplus(loadVector(Type.class, type.paramTypes.stream()
                                .map(t -> t.accept(this)).collect(toPVector())),
                            type.returnType.accept(this)));
                }

                @Override
                public Instructions visit(rho.types.Type.TypeVar type) {
                    return mv -> {
                        mv.visitVarInsn(ALOAD, typeVars.get(type));
                    };
                }

                @Override
                public Instructions visit(rho.types.Type.DataTypeType type) {
                    return newObject(fromClass(rho.types.Type.DataTypeType.class), vectorOf(Symbol.class, Class.class),
                        mplus(
                            loadSymbol(type.name),
                            type.javaType == null ? loadNull() : loadClass(type.javaType)
                        ));
                }
            }),

            mplus(typeVars_.entrySet().stream()
                .map(e -> (Instructions) mv -> {
                    mv.visitInsn(ACONST_NULL);
                    mv.visitVarInsn(ASTORE, e.getValue());
                }).collect(toPVector())));
    }

    static Instructions loadDataType(DataType<? extends rho.types.Type> dataType, Locals locals) {
        return newObject(fromClass(DataType.class), vectorOf(Object.class, Symbol.class, PVector.class), mplus(
            loadType(dataType.type, locals),
            loadSymbol(dataType.sym),
            loadVector(DataTypeConstructor.class, dataType.constructors.stream()
                .map(c -> c.accept(new ConstructorVisitor<rho.types.Type, Instructions>() {
                    @Override
                    public Instructions visit(DataTypeConstructor.ValueConstructor<? extends rho.types.Type> constructor) {
                        return newObject(fromClass(DataTypeConstructor.ValueConstructor.class), vectorOf(Object.class, Symbol.class),
                            mplus(
                                loadType(constructor.type, locals),
                                loadSymbol(c.sym)));
                    }

                    @Override
                    public Instructions visit(DataTypeConstructor.VectorConstructor<? extends rho.types.Type> constructor) {
                        return newObject(fromClass(DataTypeConstructor.VectorConstructor.class), vectorOf(Object.class, Symbol.class, PVector.class),
                            mplus(
                                loadType(constructor.type, locals),
                                loadSymbol(c.sym),
                                loadVector(rho.types.Type.class, constructor.paramTypes.stream().map(pt -> loadType(pt, locals)).collect(toPVector()))));
                    }
                }))

                .collect(toPVector()))
        ));
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

    static Instructions box(Type type) {
        switch (type.getSort()) {
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
            Type type = Type.getType(clazz);

            for (int i = 0; i < instructions.size(); i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                instructions.get(i).apply(mv);
                box(type).apply(mv);
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

    static Instructions loadMap(PVector<Instructions> instructions) {
        return mplus(
            newObject(fromClass(HashMap.class), Empty.vector(), MZERO),
            mplus(instructions.stream()
                .map(i ->
                    mplus(
                        mv -> mv.visitInsn(DUP),
                        i,
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

    static Instructions letBinding(Instructions instructions, Class<?> clazz, Locals.Local.VarLocal local) {
        return mplus(instructions,
            mv -> mv.visitVarInsn(Type.getType(clazz).getOpcode(ISTORE), local.idx));
    }

    static Instructions localVarCall(Locals.Local local) {
        return local.accept(new Locals.Local.LocalVisitor<Instructions>() {
            @Override
            public Instructions visit(Locals.Local.FieldLocal local) {
                return mplus(
                    mv -> mv.visitVarInsn(ALOAD, 0),
                    fieldOp(GET_FIELD, local.owner, local.fieldName, fromClass(local.clazz)));
            }

            @Override
            public Instructions visit(Locals.Local.VarLocal local) {
                return mv -> mv.visitVarInsn(Type.getType(local.clazz).getOpcode(ILOAD), local.idx);
            }
        });
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

    static Instructions staticMethodHandle(String classInternalName, String methodName, PVector<Class<?>> paramClasses, Class<?> returnClass) {
        return loadObject(new Handle(H_INVOKESTATIC, classInternalName, methodName,
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
