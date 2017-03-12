package bridje.compiler;

import org.pcollections.PSet;
import org.pcollections.PVector;

final class NewMethod {
    public final PSet<AccessFlag> flags;
    public final String name;
    public final Class<?> returnType;
    public final PVector<Class<?>> parameterTypes;
    public final Instructions instructions;

    NewMethod(PSet<AccessFlag> flags, String name, Class<?> returnType, PVector<Class<?>> parameterTypes, Instructions instructions) {
        this.flags = flags;
        this.name = name;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.instructions = instructions;
    }

    static NewMethod newMethod(PSet<AccessFlag> flags, String name, Class<?> returnType, PVector<Class<?>> parameterTypes, Instructions instructions) {
        return new NewMethod(
            flags,
            name,
            returnType,
            parameterTypes,
            instructions);
    }
}
