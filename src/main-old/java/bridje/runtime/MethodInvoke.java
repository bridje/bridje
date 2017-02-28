package bridje.runtime;

import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.*;

public enum MethodInvoke {
    INVOKE_STATIC(Opcodes.INVOKESTATIC, false), INVOKE_VIRTUAL(INVOKEVIRTUAL, false), INVOKE_SPECIAL(INVOKESPECIAL, false), INVOKE_INTERFACE(INVOKEINTERFACE, true);

    public final int opcode;
    public final boolean isInterface;

    MethodInvoke(int opcode, boolean isInterface) {
        this.opcode = opcode;
        this.isInterface = isInterface;
    }
}
