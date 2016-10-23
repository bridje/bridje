package rho.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import rho.runtime.Env;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

import static org.objectweb.asm.Opcodes.V1_8;
import static rho.Panic.panic;

class ClassDefiner extends ClassLoader {

    private static AtomicLong UNIQUE_INTS = new AtomicLong(1000);

    private static long uniqueInt() {
        return UNIQUE_INTS.getAndIncrement();
    }

    private static final ClassDefiner LOADER = new ClassDefiner();

    private static String toInternalName(String name) {
        return name.replace('.', '/');
    }

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

    static Class<?> defineClass(Env env, NewClass newClass) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        String className = newClass.name + "$$" + uniqueInt();

        cw.visit(V1_8, AccessFlag.toInt(newClass.flags), toInternalName(className), null, toInternalName(newClass.superClassName), null);

        for (NewMethod method : newClass.methods) {
            MethodVisitor mv = cw.visitMethod(AccessFlag.toInt(method.flags), method.name, method.descriptor, null, method.exceptions);

            for (Instruction i : method.instructions) {
                i.apply(mv);
            }

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        writeClassFile(className, bytes);
        return LOADER.defineClass(className, bytes, 0, bytes.length);
    }

}
