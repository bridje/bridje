package rho.compiler;

import org.pcollections.PSet;
import org.pcollections.PVector;

import static rho.Util.setOf;
import static rho.compiler.AccessFlag.FINAL;
import static rho.compiler.AccessFlag.PUBLIC;

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

    static NewMethod newMethod(String name, Class<?> returnType, PVector<Class<?>> parameterTypes, Instructions instructions) {
        return new NewMethod(
            setOf(PUBLIC, FINAL),
            name,
            returnType,
            parameterTypes,
            instructions);
    }

    NewMethod withFlags(PSet<AccessFlag> flags) {
        return new NewMethod(flags, name, returnType, parameterTypes, instructions);
    }
}
