package rho.compiler;

import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.PSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.runtime.IndyBootstrap;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
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

    public static NewClass newBootstrapClass(rho.types.Type type) {
        String className = "$$bootstrap" + uniqueInt();

        rho.types.Type.FnType fnType = type instanceof rho.types.Type.FnType
            ? (rho.types.Type.FnType) type
            : null;

        return newClass(className)
            .withInterfaces(setOf(IndyBootstrap.class))
            .withField(newField("delegate", IndyBootstrap.Delegate.class, setOf(PRIVATE, FINAL, STATIC)))

            .withMethod(newMethod("<clinit>", Void.TYPE, vectorOf(),
                mplus(
                    newObject(IndyBootstrap.Delegate.class, vectorOf(Class.class, MethodType.class),
                        mplus(
                            loadClass(type.javaType()),
                            fnType != null
                                ? loadObject(Type.getMethodType(Type.getType(fnType.returnType.javaType()), fnType.paramTypes.stream().map(pt -> Type.getType(pt.javaType())).toArray(Type[]::new)))
                                : mv -> mv.visitInsn(ACONST_NULL))),

                    fieldOp(FieldOp.PUT_STATIC, className, "delegate", IndyBootstrap.Delegate.class),

                    ret(Void.TYPE)))
                .withFlags(setOf(PUBLIC, STATIC)))

            .withMethod(newMethod("<init>", Void.TYPE, vectorOf(),
                mplus(
                    loadThis(),
                    methodCall(Object.class, INVOKE_SPECIAL, "<init>", Void.TYPE, vectorOf()),
                    ret(Void.TYPE)))
                .withFlags(setOf(PUBLIC)))

            .withMethod(newMethod(BOOTSTRAP_METHOD_NAME, BOOTSTRAP_METHOD_TYPE.returnType(), TreePVector.from(BOOTSTRAP_METHOD_TYPE.parameterList()),
                mplus(
                    fieldOp(GET_STATIC, className, "delegate", IndyBootstrap.Delegate.class),
                    mv -> mv.visitVarInsn(ALOAD, 1), // name
                    methodCall(IndyBootstrap.Delegate.class, INVOKE_VIRTUAL, "bootstrap", CallSite.class, vectorOf(String.class)),
                    ret(CallSite.class)))
                .withFlags(setOf(PUBLIC, STATIC)))

            .withMethod(newMethod("setHandles", Void.TYPE, vectorOf(MethodHandle.class, MethodHandle.class),
                mplus(
                    fieldOp(GET_STATIC, className, "delegate", IndyBootstrap.Delegate.class),
                    mv -> {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(ALOAD, 2);
                    },
                    methodCall(IndyBootstrap.class, INVOKE_INTERFACE, "setHandles", Void.TYPE, vectorOf(MethodHandle.class, MethodHandle.class)),
                    ret(Void.TYPE)))
                .withFlags(setOf(PUBLIC, FINAL)));
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
