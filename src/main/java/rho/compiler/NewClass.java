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
    public final PSet<String> interfaceNames;
    public final PVector<NewField> fields;
    public final PVector<NewMethod> methods;

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

}
