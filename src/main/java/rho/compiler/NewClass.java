package rho.compiler;

import org.pcollections.Empty;
import org.pcollections.PSet;
import org.pcollections.PVector;

import static rho.Util.setOf;
import static rho.compiler.AccessFlag.FINAL;
import static rho.compiler.AccessFlag.PUBLIC;

class NewClass {
    public final String name;
    public final PSet<AccessFlag> flags;
    public final String superClassName;
    public final PVector<NewMethod> methods;

    public static NewClass newClass(String name) {
        return new NewClass(name, setOf(PUBLIC, FINAL), Object.class.getName(), Empty.vector());
    }

    private NewClass(String name, PSet<AccessFlag> flags, String superClassName, PVector<NewMethod> methods) {
        this.name = name;
        this.flags = flags;
        this.superClassName = superClassName;
        this.methods = methods;
    }

    public NewClass withSuperclass(String superClassName) {
        return new NewClass(name, flags, superClassName, methods);
    }

    public NewClass withMethod(NewMethod method) {
        return new NewClass(name, flags, superClassName, methods.plus(method));
    }
}
