package rho.compiler;

import org.objectweb.asm.Type;
import org.pcollections.PSet;
import org.pcollections.PVector;

import static rho.Util.setOf;
import static rho.compiler.AccessFlag.FINAL;
import static rho.compiler.AccessFlag.PUBLIC;

final class NewMethod {
    public final PSet<AccessFlag> flags;
    public final String name;
    public final String descriptor;
    public final String[] exceptions;
    public final PVector<Instruction> instructions;

    NewMethod(PSet<AccessFlag> flags, String name, String descriptor, String[] exceptions, PVector<Instruction> instructions) {
        this.flags = flags;
        this.name = name;
        this.descriptor = descriptor;
        this.exceptions = exceptions;
        this.instructions = instructions;
    }

    static NewMethod newMethod(String name, Class<?> returnType, PVector<Class<?>> parameterTypes, PVector<Instruction> instructions) {
        return new NewMethod(
            setOf(PUBLIC, FINAL),
            name,
            Type.getMethodDescriptor(Type.getType(returnType), parameterTypes.stream().map(Type::getType).toArray(Type[]::new)),
            null,
            instructions);
    }

    NewMethod withFlags(PSet<AccessFlag> flags) {
        return new NewMethod(flags, name, descriptor, exceptions, instructions);
    }
}
