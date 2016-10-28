package rho.compiler;

import org.objectweb.asm.*;
import org.pcollections.HashTreePSet;
import org.pcollections.MapPSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.Util;
import rho.runtime.BootstrapMethod;
import rho.runtime.Var;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static rho.compiler.Instructions.MethodInvoke.INVOKE_STATIC;

interface Instructions {
    void apply(MethodVisitor mv);

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

    enum MethodInvoke {
        INVOKE_STATIC(Opcodes.INVOKESTATIC, false), INVOKE_VIRTUAL(Opcodes.INVOKEVIRTUAL, false);

        final int opcode;
        final boolean isInterface;

        MethodInvoke(int opcode, boolean isInterface) {
            this.opcode = opcode;
            this.isInterface = isInterface;
        }
    }

    static Instructions methodCall(Class<?> clazz, MethodInvoke methodInvoke, String name, Class<?> returnType, PVector<Class<?>> paramTypes) {
        return mv ->
            mv.visitMethodInsn(methodInvoke.opcode, Type.getType(clazz).getInternalName(), name,
                Type.getMethodDescriptor(Type.getType(returnType), paramTypes.stream().map(Type::getType).toArray(Type[]::new)),
                methodInvoke.isInterface);
    }

    static Instructions arrayOf(Class<?> clazz, PVector<Instructions> instructions) {
        return mv -> {
            mv.visitLdcInsn(instructions.size());
            mv.visitTypeInsn(ANEWARRAY, Type.getType(clazz).getInternalName());
            for (int i = 0; i < instructions.size(); i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                instructions.get(i).apply(mv);
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
        return mv -> {
            BootstrapMethod bootstrapMethod = var.bootstrapMethod;
            Handle handle = new Handle(H_INVOKESTATIC, Type.getType(bootstrapMethod.bootstrapClass).getInternalName(), bootstrapMethod.bootstrapMethodName, bootstrapMethod.methodType.toMethodDescriptorString(), false);

            mv.visitInvokeDynamicInsn("invoke", var.methodType.toMethodDescriptorString(), handle);
        };
    }

    static Instructions varCall(Var var, PVector<Instructions> paramInstructions) {
        return mplus(
            mplus(paramInstructions),
            varInvoke(var));
    }

    static Instructions ret(rho.types.Type type) {
        return mv -> mv.visitInsn(Type.getType(type.javaType()).getOpcode(IRETURN));
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

    static Instructions letBinding(Instructions instructions, Locals.Local local) {
        return mplus(instructions,
            mv -> mv.visitVarInsn(ASTORE, local.idx));
    }

    static Instructions localVarCall(Locals.Local local) {
        return mv -> mv.visitVarInsn(ALOAD, local.idx);
    }
}
