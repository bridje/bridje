package rho.compiler;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.PSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.Panic;
import rho.runtime.IndyBootstrap;
import rho.runtime.Var;
import rho.types.ValueType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import static org.objectweb.asm.Opcodes.*;
import static rho.Util.setOf;
import static rho.Util.*;
import static rho.Util.vectorOf;
import static rho.compiler.AccessFlag.*;
import static rho.compiler.Instructions.*;
import static rho.compiler.Instructions.FieldOp.GET_STATIC;
import static rho.compiler.Instructions.MethodInvoke.*;
import static rho.compiler.NewField.newField;
import static rho.compiler.NewMethod.newMethod;
import static rho.runtime.Var.BOOTSTRAP_METHOD_NAME;
import static rho.runtime.Var.BOOTSTRAP_METHOD_TYPE;

class NewClass {
    public final String name;
    public final PSet<AccessFlag> flags;
    public final String superClassName;
    public final PSet<String> interfaceNames;
    public final PVector<NewField> fields;
    public final PVector<NewMethod> methods;

    private static final class __Bootstrap implements IndyBootstrap {
//        private static final MutableCallSite VALUE_CALL_SITE;
//        private static final MutableCallSite FN_CALL_SITE;

        static {
//            VALUE_CALL_SITE = new MutableCallSite();
//            FN_CALL_SITE = new MutableCallSite();
        }

        @Override
        public void setHandles(MethodHandle valueHandle, MethodHandle fnHandle) {
//            VALUE_CALL_SITE.setTarget(valueHandle);
//            FN_CALL_SITE.setTarget();
        }
    }

    public static NewClass newBootstrapClass(ValueType valueType) {
        String className = "$$bootstrap" + uniqueInt();


        return newClass(className)
            .withInterfaces(setOf(IndyBootstrap.class))

            .withField(newField("VALUE_CALLSITE", MutableCallSite.class, setOf(PRIVATE, STATIC, FINAL)))
//            .withField(newField("FN_CALLSITE", MutableCallSite.class, setOf(PRIVATE, STATIC, FINAL)))

            .withMethod(newMethod("<clinit>", Void.TYPE, vectorOf(),
                mplus(
                    newObject(MutableCallSite.class, vectorOf(MethodType.class),
                        mplus(
                            loadClass(valueType.javaType()),
                            methodCall(MethodType.class, INVOKE_STATIC, "methodType", MethodType.class, vectorOf(Class.class))
                        )
                    ),
                    fieldOp(FieldOp.PUT_STATIC, className, "VALUE_CALLSITE", MutableCallSite.class),

//                    newObject(MutableCallSite.class, vectorOf(MethodType.class),
//                        mplus(
//                            (mv, stackTypes, localTypes) -> {
//                                mv.visitLdcInsn(Type.getObjectType(Type.getType(MethodType.class).getInternalName()));
//                                stackTypes.push(Type.getType(Class.class));
//                                mv.visitLdcInsn(Type.getObjectType(Type.getType(Class.class).getInternalName()));
//                                stackTypes.push(Type.getType(Class.class));
//                            },
//
//                            methodCall(MethodType.class, INVOKE_STATIC, "methodType", MethodType.class, vectorOf(Class.class, Class.class))
//                        )
//                    ),
//
//                    fieldOp(FieldOp.PUT_STATIC, className, "FN_CALLSITE", MutableCallSite.class),

                    ret(Void.TYPE)))
                .withFlags(setOf(PUBLIC, STATIC)))

            .withMethod(newMethod("<init>", Void.TYPE, vectorOf(),
                mplus(
                    loadThis(),
                    methodCall(Object.class, INVOKE_SPECIAL, "<init>", Void.TYPE, vectorOf()),
                    ret(Void.TYPE)))
                .withFlags(setOf(PUBLIC)))

            .withMethod(newMethod(BOOTSTRAP_METHOD_NAME, BOOTSTRAP_METHOD_TYPE.returnType(), TreePVector.from(BOOTSTRAP_METHOD_TYPE.parameterList()),
                (mv, st, lt) -> {
                    Label tryValue = new Label();
                    Label fail = new Label();

                    Type stringType = Type.getType(String.class);

                    mv.visitLdcInsn(Var.FN_METHOD_NAME);
                    st.push(stringType);
                    mv.visitVarInsn(ALOAD, 1); // name
                    st.push(stringType);
                    methodCall(Object.class, INVOKE_VIRTUAL, "equals", Boolean.TYPE, vectorOf(Object.class)).apply(mv, st, lt);
                    mv.visitJumpInsn(IFEQ, tryValue);
                    st.pop();
                    st.push(Type.BOOLEAN_TYPE);
                    fieldOp(GET_STATIC, className, "FN_CALLSITE", MutableCallSite.class).apply(mv, st, lt);
                    mv.visitInsn(ARETURN);

                    mv.visitLabel(tryValue);
                    mv.visitLdcInsn(Var.VALUE_METHOD_NAME);
                    st.push(stringType);
                    mv.visitVarInsn(ALOAD, 1); // name
                    st.push(stringType);
                    methodCall(Object.class, INVOKE_VIRTUAL, "equals", Boolean.TYPE, vectorOf(Object.class)).apply(mv, st, lt);
                    mv.visitJumpInsn(IFEQ, fail);
                    st.pop();
                    st.push(Type.BOOLEAN_TYPE);
                    fieldOp(GET_STATIC, className, "VALUE_CALLSITE", MutableCallSite.class).apply(mv, st, lt);
                    mv.visitInsn(ARETURN);

                    mv.visitLabel(fail);
                    mv.visitLdcInsn("Invalid bootstrap name");
                    mv.visitInsn(ICONST_0);
                    mv.visitTypeInsn(ANEWARRAY, Type.getType(Object.class).getInternalName());
                    methodCall(Panic.class, INVOKE_STATIC, "panic", Panic.class, vectorOf(String.class, Object[].class)).apply(mv, st, lt);
                    mv.visitInsn(ATHROW);
                }).withFlags(setOf(PUBLIC, STATIC)))

            .withMethod(newMethod("setHandles", Void.TYPE, vectorOf(MethodHandle.class, MethodHandle.class),
                (mv, stackTypes, localTypes) -> {
                    fieldOp(GET_STATIC, className, "VALUE_CALLSITE", MutableCallSite.class).apply(mv, stackTypes, localTypes);
                    mv.visitVarInsn(ALOAD, 1);
                    stackTypes.push(Type.getType(MethodHandle.class));
                    methodCall(MutableCallSite.class, INVOKE_VIRTUAL, "setTarget", Void.TYPE, vectorOf(MethodHandle.class)).apply(mv, stackTypes, localTypes);
                    mv.visitInsn(RETURN);
                }));
    }

    public static NewClass newClass(String name) {
        return new NewClass(name, setOf(PUBLIC, FINAL), Object.class.getName(), Empty.set(), Empty.vector(), Empty.vector());
    }

    private NewClass(String name, PSet<AccessFlag> flags, String superClassName, PSet<String> interfaceClassNames, PVector<NewField> fields, PVector<NewMethod> methods) {
        this.name = name;
        this.flags = flags;
        this.superClassName = superClassName;
        this.interfaceNames = interfaceClassNames;
        this.fields = fields;
        this.methods = methods;
    }

    public NewClass withSuperclass(String superClassName) {
        return new NewClass(name, flags, superClassName, interfaceNames, fields, methods);
    }

    public NewClass withMethod(NewMethod method) {
        return new NewClass(name, flags, superClassName, interfaceNames, fields, methods.plus(method));
    }

    public NewClass withField(NewField field) {
        return new NewClass(name, flags, superClassName, interfaceNames, fields.plus(field), methods);
    }

    public NewClass withInterfaces(PSet<Class<?>> interfaces) {
        return new NewClass(name, flags, superClassName, interfaceNames.plusAll(interfaces.stream().map(Class::getName).collect(toPSet())), fields, methods);
    }
}
