package rho.compiler;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.pcollections.PVector;

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
        return new Instruction() {
            @Override
            public void apply(MethodVisitor mv) {
                mv.visitMethodInsn(methodInvoke.opcode, Type.getType(clazz).getInternalName(), name,
                    Type.getMethodDescriptor(Type.getType(returnType), paramTypes.stream().map(Type::getType).toArray(Type[]::new)),
                    methodInvoke.isInterface);
            }
        };
    }
}
