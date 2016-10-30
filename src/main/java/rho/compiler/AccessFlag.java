package rho.compiler;

import org.pcollections.PSet;

import static org.objectweb.asm.Opcodes.*;

enum AccessFlag {
    PUBLIC(ACC_PUBLIC), FINAL(ACC_FINAL), STATIC(ACC_STATIC), PRIVATE(ACC_PRIVATE);

    final int flag;

    AccessFlag(int flag) {
        this.flag = flag;
    }

    static int toInt(PSet<AccessFlag> flags) {
        int access = 0;

        for (AccessFlag flag : flags) {
            access |= flag.flag;
        }

        return access;
    }
}
