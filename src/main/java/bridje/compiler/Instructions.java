package bridje.compiler;

import bridje.runtime.java.MethodInvoke;
import bridje.util.ClassLike;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.PVector;

import java.util.Arrays;

import static bridje.Util.vectorOf;
import static bridje.compiler.JavaSort.LONG;
import static bridje.compiler.JavaSort.OBJECT;
import static bridje.util.ClassLike.fromClass;
import static org.objectweb.asm.Opcodes.*;

interface Instructions {
    void apply(MethodVisitor mv);

    Instructions MZERO = mv -> {
    };

    static Instructions mplus(Iterable<Instructions> instructionses) {
        return mv -> {
            for (Instructions instructions : instructionses) {
                instructions.apply(mv);
            }
        };
    }

    static Instructions mplus(Instructions... instructionses) {
        return mplus(Arrays.asList(instructionses));
    }

    static Instructions loadBool(boolean bool) {
        return mv -> mv.visitInsn(bool ? ICONST_1 : ICONST_0);
    }

    static Instructions loadString(String str) {
        return mv -> mv.visitLdcInsn(str);
    }

    static Instructions loadLong(long num) {
        return mv -> mv.visitLdcInsn(num);
    }

    static Instructions methodCall(ClassLike classLike, MethodInvoke methodInvoke, String name, Class<?> returnType, PVector<Class<?>> paramTypes) {
        if (returnType == null || paramTypes.stream().anyMatch(pt -> pt == null)) {
            throw new UnsupportedOperationException();
        }
        return mv -> mv.visitMethodInsn(methodInvoke.opcode, classLike.getInternalName(), name,
            Type.getMethodDescriptor(Type.getType(returnType), paramTypes.stream().map(Type::getType).toArray(Type[]::new)),
            methodInvoke.isInterface);
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

    static Instructions[][] makeCoerces() {
        int sortCount = JavaSort.values().length;
        Instructions[][] coerces = new Instructions[sortCount][sortCount];
        int objOrd = OBJECT.ordinal();
        int longOrd = LONG.ordinal();

        coerces[objOrd][longOrd] = methodCall(fromClass(Long.class), MethodInvoke.INVOKE_VIRTUAL, "longValue", Long.class, Empty.vector());
        coerces[longOrd][objOrd] = methodCall(fromClass(Long.class), MethodInvoke.INVOKE_STATIC, "valueOf", Long.TYPE, vectorOf(Long.class));

        return coerces;
    }

    Instructions[][] COERCES = makeCoerces();

    static Instructions coerce(JavaSort source, JavaSort target) {
        if (target == null || source == target) return MZERO;

        return COERCES[source.ordinal()][target.ordinal()];
    }
}
