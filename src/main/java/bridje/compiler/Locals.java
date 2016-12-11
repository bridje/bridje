package bridje.compiler;

import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.PMap;

import static bridje.Util.toPVector;
import static bridje.compiler.Instructions.FieldOp.GET_FIELD;
import static bridje.compiler.Instructions.FieldOp.PUT_FIELD;
import static bridje.compiler.Instructions.*;
import static org.objectweb.asm.Opcodes.*;

final class Locals {

    interface LocalInstructions {
        Instructions load();

        Instructions store(Instructions instructions);

        default Instructions clear() {
            return store(mv -> mv.visitInsn(ACONST_NULL));
        }
    }

    private class VarLocal implements LocalInstructions {
        private final Type type;

        public VarLocal(Type type) {
            this.type = type;
        }

        @Override
        public Instructions load() {
            return mv -> mv.visitVarInsn(type.getOpcode(ILOAD), nextIdx);
        }

        @Override
        public Instructions store(Instructions instructions) {
            return mplus(
                instructions,
                mv -> mv.visitVarInsn(type.getOpcode(ISTORE), nextIdx)
            );
        }
    }

    private static class FieldLocal implements LocalInstructions {

        final ClassLike owner;
        final String fieldName;
        final ClassLike clazz;

        FieldLocal(ClassLike owner, String fieldName, ClassLike clazz) {
            this.owner = owner;
            this.fieldName = fieldName;
            this.clazz = clazz;
        }

        @Override
        public Instructions load() {
            return mplus(
                loadThis(),
                fieldOp(GET_FIELD, owner, fieldName, clazz));

        }

        @Override
        public Instructions store(Instructions instructions) {
            return mplus(
                loadThis(),
                instructions,
                fieldOp(PUT_FIELD, owner, fieldName, clazz));
        }
    }

    private final PMap<Object, LocalInstructions> localInstructions;
    private final int nextIdx;

    static Locals staticLocals() {
        return new Locals(Empty.map(), 0);
    }

    static Locals instanceLocals() {
        return new Locals(Empty.map(), 1);
    }

    private Locals(PMap<Object, LocalInstructions> localInstructions, int nextIdx) {
        this.localInstructions = localInstructions;
        this.nextIdx = nextIdx;
    }

    public LocalInstructions get(Object token) {
        return localInstructions.get(token);
    }

    Locals withVarLocal(Object token, Class<?> clazz) {
        Type type = Type.getType(clazz);
        return new Locals(localInstructions.plus(token, new VarLocal(type)),
            nextIdx + type.getSize());
    }

    Locals withFieldLocal(Object token, ClassLike owner, String fieldName, ClassLike clazz) {
        return new Locals(localInstructions.plus(token,
            new FieldLocal(owner, fieldName, clazz)), nextIdx);
    }

    Locals split() {
        return new Locals(Empty.map(), nextIdx);
    }

    Instructions clear() {
        return mplus(localInstructions.values().stream().map(LocalInstructions::clear).collect(toPVector()));
    }
}
