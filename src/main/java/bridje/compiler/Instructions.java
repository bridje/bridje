package bridje.compiler;

import bridje.Util;
import bridje.runtime.*;
import bridje.util.Pair;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.pcollections.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static bridje.Util.toPVector;
import static bridje.Util.vectorOf;
import static bridje.compiler.ClassLike.fromClass;
import static bridje.compiler.Instructions.FieldOp.GET_STATIC;
import static bridje.runtime.MethodInvoke.*;
import static org.objectweb.asm.Opcodes.*;

interface Instructions {

    Type OBJECT_TYPE = Type.getType(Object.class);

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

    static <T extends Enum<T>> Instructions loadEnum(Class<T> clazz, T val) {
        return fieldOp(GET_STATIC, fromClass(clazz), val.name(), fromClass(clazz));
    }

    static Instructions loadNS(NS ns) {
        return mplus(loadObject(ns.name), methodCall(fromClass(NS.class), INVOKE_STATIC, "ns", NS.class, vectorOf(String.class)));
    }

    static Instructions loadSymbol(Symbol sym) {
        return newObject(fromClass(Symbol.class), Util.vectorOf(String.class), loadObject(sym.sym));
    }

    static Instructions loadSymbol(QSymbol sym) {
        return newObject(fromClass(QSymbol.class), Util.vectorOf(String.class, String.class),
            mplus(loadObject(sym.ns), loadObject(sym.symbol)));
    }

    static Instructions loadFQSymbol(FQSymbol sym) {
        return mplus(loadNS(sym.ns), loadSymbol(sym.symbol), methodCall(fromClass(FQSymbol.class), INVOKE_STATIC, "fqSym", FQSymbol.class, vectorOf(NS.class, Symbol.class)));
    }

    static Instructions loadDataType(DataType dataType) {
        return newObject(fromClass(DataType.class), vectorOf(Object.class, FQSymbol.class, PVector.class, PVector.class), mplus(
            loadFQSymbol(dataType.sym),
            loadVector(dataType.constructors.stream()
                .map(c -> c.accept(new ConstructorVisitor<Instructions>() {
                    @Override
                    public Instructions visit(DataTypeConstructor.ValueConstructor constructor) {
                        return newObject(fromClass(DataTypeConstructor.ValueConstructor.class), vectorOf(Object.class, FQSymbol.class), loadFQSymbol(c.sym));
                    }

                    @Override
                    public Instructions visit(DataTypeConstructor.VectorConstructor constructor) {
                        return newObject(fromClass(DataTypeConstructor.VectorConstructor.class), vectorOf(Object.class, FQSymbol.class, PVector.class),
                            loadFQSymbol(c.sym));
                    }
                }))

                .collect(toPVector()))));
    }

    static Instructions loadClass(Class clazz) {
        Type type = Type.getType(clazz);
        switch (type.getSort()) {
            case Type.LONG:
                return fieldOp(GET_STATIC, fromClass(Long.class), "TYPE", fromClass(Class.class));
            case Type.BOOLEAN:
                return fieldOp(GET_STATIC, fromClass(Boolean.class), "TYPE", fromClass(Class.class));
            case Type.OBJECT:
                return mv -> mv.visitLdcInsn(type);
            default:
                throw new UnsupportedOperationException();
        }
    }

    static Instructions loadNull() {
        return mv -> mv.visitInsn(ACONST_NULL);
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

    static Instructions coerce(Type from, Type to) {
        int fromSort = from.getSort();
        int toSort = to.getSort();

        if (fromSort == toSort && fromSort != Type.OBJECT) {
            return MZERO;
        }

        switch (fromSort) {
            case Type.OBJECT:
                switch (toSort) {
                    case Type.OBJECT:
                        if (OBJECT_TYPE.equals(to)) {
                            return MZERO;
                        } else {
                            return mv -> mv.visitTypeInsn(CHECKCAST, to.getInternalName());
                        }
                }

                break;

            case Type.LONG:
                switch (toSort) {
                    case Type.OBJECT:
                        return methodCall(fromClass(Long.class), INVOKE_STATIC, "valueOf", Long.class, Util.vectorOf(Long.TYPE));
                }
                break;

            case Type.BOOLEAN:
                switch (toSort) {
                    case Type.OBJECT:
                        return methodCall(fromClass(Boolean.class), INVOKE_STATIC, "valueOf", Boolean.class, Util.vectorOf(Boolean.TYPE));
                }
                break;
        }

        throw new UnsupportedOperationException();
    }

    static Instructions arrayOf(PVector<Instructions> instructions) {
        return mv -> {
            mv.visitLdcInsn(instructions.size());
            mv.visitTypeInsn(ANEWARRAY, OBJECT_TYPE.getInternalName());

            for (int i = 0; i < instructions.size(); i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                instructions.get(i).apply(mv);
                mv.visitInsn(AASTORE);
            }
        };
    }

    Instructions ARRAY_AS_LIST = methodCall(fromClass(Arrays.class), INVOKE_STATIC, "asList", List.class, Util.vectorOf(Object[].class));

    static Instructions loadVector(PVector<Instructions> instructions) {
        return mplus(
            arrayOf(instructions),
            ARRAY_AS_LIST,
            methodCall(fromClass(TreePVector.class), INVOKE_STATIC, "from", TreePVector.class, Util.vectorOf(Collection.class)));
    }

    static Instructions loadSet(PVector<Instructions> instructions) {
        return mplus(
            arrayOf(instructions),
            ARRAY_AS_LIST,
            methodCall(fromClass(HashTreePSet.class), INVOKE_STATIC, "from", MapPSet.class, Util.vectorOf(Collection.class)));
    }

    static Instructions loadMap(PVector<Pair<Instructions, Instructions>> instructions) {
        return mplus(
            newObject(fromClass(HashMap.class), Empty.vector(), MZERO),
            mplus(instructions.stream()
                .map(i ->
                    mplus(
                        mv -> mv.visitInsn(DUP),
                        i.left,
                        i.right,
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

    static Instructions ret(Type type) {
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
        return mv -> mv.visitFieldInsn(op.opcode, owner.getInternalName(), fieldName, fieldType.getDescriptor());
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
