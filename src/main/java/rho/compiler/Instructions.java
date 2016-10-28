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
import java.util.Stack;

import static org.objectweb.asm.Opcodes.*;
import static rho.compiler.Instructions.MethodInvoke.INVOKE_STATIC;

interface Instructions {
    void apply(MethodVisitor mv, Stack<Type> stackTypes, List<Type> localTypes);

    static Instructions mplus(Iterable<Instructions> instructions) {
        return (mv, st, lt) -> {
            for (Instructions instruction : instructions) {
                instruction.apply(mv, st, lt);
            }
        };
    }

    static Instructions mplus(Instructions... instructions) {
        return mplus(Arrays.asList(instructions));
    }

    static Instructions loadObject(Object obj, Class<?> clazz) {
        return (mv, st, lt) -> {
            mv.visitLdcInsn(obj);
            st.push(Type.getType(clazz));
        };
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
        return (mv, st, lt) -> {
            paramTypes.forEach(pt -> st.pop());
            st.push(Type.getType(returnType));

            mv.visitMethodInsn(methodInvoke.opcode, Type.getType(clazz).getInternalName(), name,
                Type.getMethodDescriptor(Type.getType(returnType), paramTypes.stream().map(Type::getType).toArray(Type[]::new)),
                methodInvoke.isInterface);
        };

    }

    static Instructions box(Type type) {
        switch (type.getOpcode(ISTORE)) {
            case ASTORE:
                return mplus();
            case LSTORE:
                return methodCall(Long.class, INVOKE_STATIC, "valueOf", Long.class, Util.vectorOf(Long.TYPE));
        }

        throw new UnsupportedOperationException();
    }

    static Instructions arrayOf(PVector<Instructions> instructions) {
        return (mv, st, lt) -> {
            mv.visitLdcInsn(instructions.size());
            Type type = Type.getType(Object.class);
            mv.visitTypeInsn(ANEWARRAY, type.getInternalName());
            st.push(type);
            for (int i = 0; i < instructions.size(); i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                instructions.get(i).apply(mv, st, lt);
                box(st.peek()).apply(mv, st, lt);
                mv.visitInsn(AASTORE);
            }
        };
    }

    Instructions ARRAY_AS_LIST = methodCall(Arrays.class, INVOKE_STATIC, "asList", List.class, Util.vectorOf(Object[].class));

    static Instructions vectorOf(PVector<Instructions> instructions) {
        return mplus(
            arrayOf(instructions),
            ARRAY_AS_LIST,
            methodCall(TreePVector.class, INVOKE_STATIC, "from", TreePVector.class, Util.vectorOf(Collection.class)));
    }

    static Instructions setOf(PVector<Instructions> instructions) {
        return mplus(
            arrayOf(instructions),
            ARRAY_AS_LIST,
            methodCall(HashTreePSet.class, INVOKE_STATIC, "from", MapPSet.class, Util.vectorOf(Collection.class)));
    }

    static Instructions loadBool(boolean value) {
        return (mv, st, lt) -> {
            mv.visitInsn(value ? ICONST_1 : ICONST_0);
            st.push(Type.BOOLEAN_TYPE);
        };
    }

    static Instructions varInvoke(Var var) {
        return (mv, st, lt) -> {
            BootstrapMethod bootstrapMethod = var.bootstrapMethod;
            Handle handle = new Handle(H_INVOKESTATIC, Type.getType(bootstrapMethod.bootstrapClass).getInternalName(), bootstrapMethod.bootstrapMethodName, bootstrapMethod.methodType.toMethodDescriptorString(), false);
            var.methodType.parameterList().forEach(p -> st.pop());
            st.push(Type.getType(var.methodType.returnType()));

            mv.visitInvokeDynamicInsn("invoke", var.methodType.toMethodDescriptorString(), handle);
        };
    }

    static Instructions varCall(Var var, PVector<Instructions> paramInstructions) {
        return mplus(
            mplus(paramInstructions),
            varInvoke(var));
    }

    static Instructions ret() {
        return (mv, st, lt) -> {
            mv.visitInsn(st.peek().getOpcode(IRETURN));
        };
    }

    static Instructions ifCall(Instructions testInstructions, Instructions thenInstructions, Instructions elseInstructions) {
        Label elseLabel = new Label();
        Label endLabel = new Label();
        return mplus(
            testInstructions,
            (mv, st, lt) -> mv.visitJumpInsn(IFEQ, elseLabel),
            thenInstructions,
            (mv, st, lt) -> {
                mv.visitJumpInsn(GOTO, endLabel);
                mv.visitLabel(elseLabel);
            },
            elseInstructions,
            (mv, st, lt) -> mv.visitLabel(endLabel));
    }

    static int localCount(List<Type> localTypes) {
        int count = 0;
        for (Type localType : localTypes) {
            if (localType != null) {
                count += localType.getSize();
            }
        }
        return count;
    }

    static Instructions letBinding(Instructions instructions, Locals.Local local) {
        return mplus(instructions,
            (mv, st, lt) -> {
                instructions.apply(mv, st, lt);
                Type type = st.pop();
                lt.add(local.idx, type);
                mv.visitVarInsn(type.getOpcode(ISTORE), localCount(lt.subList(0, local.idx)));
            });
    }

    static Instructions localVarCall(Locals.Local local) {
        return (mv, st, lt) -> {
            Type type = lt.get(local.idx);
            st.push(type);
            mv.visitVarInsn(type.getOpcode(ILOAD), localCount(lt.subList(0, local.idx)));
        };
    }
}
