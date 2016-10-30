package rho.compiler;

import org.objectweb.asm.*;
import org.pcollections.HashTreePSet;
import org.pcollections.MapPSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.Util;
import rho.runtime.Var;

import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.BiConsumer;

import static org.objectweb.asm.Opcodes.*;
import static rho.Util.toInternalName;
import static rho.compiler.Instructions.FieldOp.GET_STATIC;
import static rho.compiler.Instructions.MethodInvoke.INVOKE_SPECIAL;
import static rho.compiler.Instructions.MethodInvoke.INVOKE_STATIC;

interface Instructions {
    void apply(MethodVisitor mv, Stack<Type> stackTypes, List<Type> localTypes);

    Instructions MZERO = (mv, st, lt) -> {
    };

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

    static Instructions loadClass(Class clazz) {
        Type type = Type.getType(clazz);
        switch (type.getSort()) {
            case Type.LONG:
                return fieldOp(GET_STATIC, Long.class.getName(), "TYPE", Class.class);

            case Type.OBJECT:
                return (mv, stackTypes, localTypes) -> {
                    mv.visitLdcInsn(type);
                    stackTypes.push(Type.getType(Class.class));
                };
            default:
                throw new UnsupportedOperationException();
        }
    }

    enum MethodInvoke {
        INVOKE_STATIC(Opcodes.INVOKESTATIC, false), INVOKE_VIRTUAL(INVOKEVIRTUAL, false), INVOKE_SPECIAL(INVOKESPECIAL, false);

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

    static Instructions loadThis() {
        return (mv, st, lt) -> {
            st.push(lt.get(0));
            mv.visitVarInsn(ALOAD, 0);
        };
    }

    static Instructions newObject0(Class<?> clazz) {
        return newObject(clazz, Util.vectorOf(), MZERO);
    }

    static Instructions newObject(Class<?> clazz, PVector<Class<?>> params, Instructions paramInstructions) {
        return
            mplus(
                (mv, st, lt) -> {
                    Type type = Type.getType(clazz);
                    mv.visitTypeInsn(NEW, type.getInternalName());
                    mv.visitInsn(DUP);
                    st.push(type);
                    st.push(type);
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
            Handle handle = new Handle(H_INVOKESTATIC, Type.getType(var.bootstrapClass()).getInternalName(), Var.BOOTSTRAP_METHOD_NAME, Var.BOOTSTRAP_METHOD_TYPE.toMethodDescriptorString(), false);
            var.functionMethodType.parameterList().forEach(p -> st.pop());
            st.push(Type.getType(var.functionMethodType.returnType()));

            mv.visitInvokeDynamicInsn(Var.FN_METHOD_NAME, var.functionMethodType.toMethodDescriptorString(), handle);
        };
    }

    static Instructions varCall(Var var, PVector<Instructions> paramInstructions) {
        return mplus(
            mplus(paramInstructions),
            varInvoke(var));
    }

    static Instructions ret(Class<?> clazz) {
        Type type = Type.getType(clazz);
        return (mv, st, lt) -> {
            if (type.getSort() == Type.OBJECT) {
                box(st.peek()).apply(mv, st, lt);
            }

            mv.visitInsn(type.getOpcode(IRETURN));
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
        return localTypes.stream().filter(Objects::nonNull).mapToInt(Type::getSize).sum();
    }

    static Instructions letBinding(Instructions instructions, Locals.Local local) {
        return mplus(instructions,
            (mv, st, lt) -> {
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

    static Instructions globalVarValue(Var var) {
        return (mv, st, lt) -> {
            Handle handle = new Handle(H_INVOKESTATIC, Type.getType(var.bootstrapClass()).getInternalName(), Var.BOOTSTRAP_METHOD_NAME, Var.BOOTSTRAP_METHOD_TYPE.toMethodDescriptorString(), false);
            Class<?> valueType = var.type.javaType();
            st.push(Type.getType(valueType));

            mv.visitInvokeDynamicInsn(Var.VALUE_METHOD_NAME, MethodType.methodType(valueType).toMethodDescriptorString(), handle);
        };
    }

    enum StackOp {
        PUSH(Stack::push), POP((st, t) -> st.pop());

        private final BiConsumer<Stack<Type>, Type> stackUpdater;

        StackOp(BiConsumer<Stack<Type>, Type> stackUpdater) {
            this.stackUpdater = stackUpdater;
        }

        public void updateStack(Stack<Type> st, Type type) {
            stackUpdater.accept(st, type);
        }
    }

    enum FieldOp {
        PUT_STATIC(PUTSTATIC, StackOp.POP), GET_STATIC(GETSTATIC, StackOp.PUSH);

        final int opcode;
        private final StackOp stackOp;

        FieldOp(int opcode, StackOp stackOp) {
            this.opcode = opcode;
            this.stackOp = stackOp;
        }

        public void updateStack(Stack<Type> st, Type type) {
            stackOp.updateStack(st, type);
        }
    }

    static Instructions fieldOp(FieldOp op, String className, String fieldName, Class<?> clazz) {
        return (mv, st, lt) -> {
            Type type = Type.getType(clazz);
            op.updateStack(st, type);
            mv.visitFieldInsn(op.opcode, toInternalName(className), fieldName, type.getDescriptor());
        };

    }
}
