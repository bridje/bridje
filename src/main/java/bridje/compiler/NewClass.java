package bridje.compiler;

import org.pcollections.Empty;
import org.pcollections.PSet;
import org.pcollections.PVector;

import static bridje.Util.setOf;
import static bridje.compiler.AccessFlag.FINAL;
import static bridje.compiler.AccessFlag.PUBLIC;

class NewClass {
    public final String name;
    public final PSet<AccessFlag> flags;
    public final Class<?> superClass;
    public final PSet<String> interfaceNames;
    public final PVector<NewField> fields;
    public final PVector<NewMethod> methods;

    public static NewClass newClass(String name) {
        return newClass(name, setOf(PUBLIC, FINAL));
    }

    public static NewClass newClass(String name, PSet<AccessFlag> flags) {
        return new NewClass(name, flags, Object.class, Empty.set(), Empty.vector(), Empty.vector());
    }

    private NewClass(String name, PSet<AccessFlag> flags, Class<?> superClass, PSet<String> interfaceClassNames, PVector<NewField> fields, PVector<NewMethod> methods) {
        this.name = name;
        this.flags = flags;
        this.superClass = superClass;
        this.interfaceNames = interfaceClassNames;
        this.fields = fields;
        this.methods = methods;
    }

    public NewClass withMethod(NewMethod method) {
        return new NewClass(name, flags, superClass, interfaceNames, fields, methods.plus(method));
    }

    public NewClass withField(NewField field) {
        return new NewClass(name, flags, superClass, interfaceNames, fields.plus(field), methods);
    }

    public NewClass withSuperClass(Class<?> superClass) {
        return new NewClass(name, flags, superClass, interfaceNames, fields, methods);
    }

    public NewClass withInterface(Class<?> interfaceClass) {
        return new NewClass(name, flags, superClass, interfaceNames.plus(interfaceClass.getName()), fields, methods);
    }
}
