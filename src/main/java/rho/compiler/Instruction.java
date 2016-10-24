package rho.compiler;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.pcollections.HashTreePSet;
import org.pcollections.MapPSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.Util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static rho.compiler.Instruction.MethodInvoke.INVOKE_STATIC;

interface Instruction {
    void apply(MethodVisitor mv);

    enum SimpleInstruction implements Instruction {
        ARETURN(Opcodes.ARETURN);

        private final int opcode;

        SimpleInstruction(int opcode) {
            this.opcode = opcode;
        }

        @Override
        public void apply(MethodVisitor mv) {
            mv.visitInsn(opcode);
        }
    }

    static Instruction mplus(Instruction... instructions) {
        return mv -> {
            for (Instruction instruction : instructions) {
                instruction.apply(mv);
            }
        };
    }

    static Instruction loadObject(Object obj) {
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

    static Instruction methodCall(Class<?> clazz, MethodInvoke methodInvoke, String name, Class<?> returnType, PVector<Class<?>> paramTypes) {
        return mv ->
            mv.visitMethodInsn(methodInvoke.opcode, Type.getType(clazz).getInternalName(), name,
                Type.getMethodDescriptor(Type.getType(returnType), paramTypes.stream().map(Type::getType).toArray(Type[]::new)),
                methodInvoke.isInterface);
    }

    static Instruction arrayOf(Class<?> clazz, PVector<PVector<Instruction>> instructions) {
        return mv -> {
            mv.visitLdcInsn(instructions.size());
            mv.visitTypeInsn(ANEWARRAY, Type.getType(clazz).getInternalName());
            for (int i = 0; i < instructions.size(); i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                for (Instruction instruction : instructions.get(i)) {
                    instruction.apply(mv);
                }
                mv.visitInsn(AASTORE);
            }
        };
    }

    Instruction ARRAY_AS_LIST = methodCall(Arrays.class, INVOKE_STATIC, "asList", List.class, Util.vectorOf(Object[].class));

    static Instruction vectorOf(Class<?> clazz, PVector<PVector<Instruction>> instructions) {
        return mplus(
            arrayOf(clazz, instructions),
            ARRAY_AS_LIST,
            methodCall(TreePVector.class, INVOKE_STATIC, "from", TreePVector.class, Util.vectorOf(Collection.class)));
    }

    static Instruction setOf(Class<?> clazz, PVector<PVector<Instruction>> instructions) {
        return mplus(
            arrayOf(clazz, instructions),
            ARRAY_AS_LIST,
            methodCall(HashTreePSet.class, INVOKE_STATIC, "from", MapPSet.class, Util.vectorOf(Collection.class)));
    }
}
