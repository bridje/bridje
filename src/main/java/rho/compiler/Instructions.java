package rho.compiler;

import org.objectweb.asm.*;
import org.pcollections.HashTreePSet;
import org.pcollections.MapPSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.Util;
import rho.runtime.Var;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static rho.Util.toInternalName;
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

    static Instructions loadClass(Class clazz) {
        Type type = Type.getType(clazz);
        switch (type.getSort()) {
            case Type.LONG:
                return fieldOp(GET_STATIC, Long.class.getName(), "TYPE", Class.class);

            case Type.OBJECT:
                return mv -> mv.visitLdcInsn(type);
            default:
                throw new UnsupportedOperationException();
        }
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

    static Instructions methodCall(Class<?> clazz, MethodInvoke methodInvoke, String name, Class<?> returnType, PVector<Class<?>> paramTypes) {
        return mv -> mv.visitMethodInsn(methodInvoke.opcode, Type.getType(clazz).getInternalName(), name,
            Type.getMethodDescriptor(Type.getType(returnType), paramTypes.stream().map(Type::getType).toArray(Type[]::new)),
            methodInvoke.isInterface);
    }

    static Instructions loadThis() {
        return mv -> mv.visitVarInsn(ALOAD, 0);
    }

    static Instructions newObject(Class<?> clazz, PVector<Class<?>> params, Instructions paramInstructions) {
        return
            mplus(
                mv -> {
                    Type type = Type.getType(clazz);
                    mv.visitTypeInsn(NEW, type.getInternalName());
                    mv.visitInsn(DUP);
                },
                paramInstructions,
                methodCall(clazz, INVOKE_SPECIAL, "<init>", Void.TYPE, params));
    }

    static Instructions box(Type type) {
        switch (type.getSort()) {
            case Type.OBJECT:
                return MZERO;
            case Type.LONG:
                return methodCall(Long.class, INVOKE_STATIC, "valueOf", Long.class, Util.vectorOf(Long.TYPE));
            case Type.BOOLEAN:
                return methodCall(Boolean.class, INVOKE_STATIC, "valueOf", Boolean.class, Util.vectorOf(Boolean.TYPE));
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

    Instructions ARRAY_AS_LIST = methodCall(Arrays.class, INVOKE_STATIC, "asList", List.class, Util.vectorOf(Object[].class));

    static Instructions vectorOf(Class<?> clazz, PVector<Instructions> instructions) {
        return mplus(
            arrayOf(clazz, instructions),
            ARRAY_AS_LIST,
            methodCall(TreePVector.class, INVOKE_STATIC, "from", TreePVector.class, Util.vectorOf(Collection.class)));
    }

    static Instructions setOf(Class<?> clazz, PVector<Instructions> instructions) {
        return mplus(
            arrayOf(clazz, instructions),
            ARRAY_AS_LIST,
            methodCall(HashTreePSet.class, INVOKE_STATIC, "from", MapPSet.class, Util.vectorOf(Collection.class)));
    }

    static Instructions loadBool(boolean value) {
        return mv -> mv.visitInsn(value ? ICONST_1 : ICONST_0);
    }

    static Instructions varInvoke(Var var) {
        Method method = var.fnMethod;
        return methodCall(method.getDeclaringClass(), INVOKE_STATIC, method.getName(), method.getReturnType(), TreePVector.from(Arrays.asList(method.getParameterTypes())));
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
                    fieldOp(GET_FIELD, local.className, local.fieldName, local.clazz));
            }

            @Override
            public Instructions visit(Locals.Local.VarLocal local) {
                return mv -> mv.visitVarInsn(Type.getType(local.clazz).getOpcode(ILOAD), local.idx);
            }
        });
    }

    static Instructions globalVarValue(Var var) {
        Field valueField = var.valueField;
        return fieldOp(GET_STATIC, valueField.getDeclaringClass().getName(), valueField.getName(), valueField.getType());
    }

    enum FieldOp {
        PUT_STATIC(PUTSTATIC), GET_STATIC(GETSTATIC), GET_FIELD(GETFIELD), PUT_FIELD(PUTFIELD);

        final int opcode;

        FieldOp(int opcode) {
            this.opcode = opcode;
        }
    }

    static Instructions fieldOp(FieldOp op, String className, String fieldName, Class<?> clazz) {
        return mv -> {
            Type type = Type.getType(clazz);
            mv.visitFieldInsn(op.opcode, toInternalName(className), fieldName, type.getDescriptor());
        };
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
        return methodCall(MethodHandle.class, INVOKE_VIRTUAL, "bindTo", MethodHandle.class, Util.vectorOf(Object.class));
    }
}
