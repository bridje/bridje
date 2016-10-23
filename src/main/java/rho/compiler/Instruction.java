package rho.compiler;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
}
