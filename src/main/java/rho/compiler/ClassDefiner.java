package rho.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.pcollections.PVector;
import rho.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.objectweb.asm.Opcodes.V1_8;
import static rho.Panic.panic;
import static rho.Util.toInternalName;
import static rho.Util.toPVector;

class ClassDefiner extends ClassLoader {

    private static final ClassDefiner LOADER = new ClassDefiner();

    private static void writeClassFile(String name, byte[] bytes) {
        try {
            Path dir = Paths.get("target", "bytecode");
            Files.createDirectories(dir);
            try (OutputStream stream = Files.newOutputStream(dir.resolve(String.format("%s.class", name)))) {
                stream.write(bytes);
            }
        } catch (IOException e) {
            throw panic(e, "Can't write class file");
        }
    }

    static Class<?> defineClass(NewClass newClass) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cw.visit(V1_8, AccessFlag.toInt(newClass.flags), toInternalName(newClass.name), null, toInternalName(newClass.superClassName),
            newClass.interfaceNames.stream().map(Util::toInternalName).toArray(String[]::new));

        for (NewField field : newClass.fields) {
            FieldVisitor fv = cw.visitField(AccessFlag.toInt(field.flags), field.name, Type.getType(field.clazz).getDescriptor(), null, null);
            fv.visitEnd();
        }

        for (NewMethod method : newClass.methods) {
            PVector<Type> paramTypes = method.parameterTypes.stream().map(Type::getType).collect(toPVector());
            MethodVisitor mv = cw.visitMethod(AccessFlag.toInt(method.flags), method.name,
                Type.getMethodDescriptor(Type.getType(method.returnType), paramTypes.toArray(new Type[paramTypes.size()])),
                null,
                null);

            method.instructions.apply(mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        writeClassFile(newClass.name, bytes);
        return LOADER.defineClass(newClass.name, bytes, 0, bytes.length);
    }

}
