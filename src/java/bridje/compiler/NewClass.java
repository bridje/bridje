package bridje.compiler;

import bridje.util.ClassLike;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.PSet;
import org.pcollections.PVector;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static bridje.Panic.panic;
import static bridje.Util.setOf;
import static bridje.compiler.AccessFlag.*;
import static bridje.compiler.Instructions.mplus;
import static bridje.compiler.NewMethod.newMethod;
import static org.objectweb.asm.Opcodes.V1_8;

class NewClass {
    public final ClassLike clazz;
    public final PSet<AccessFlag> flags;
    public final ClassLike superClass;
    public final PSet<ClassLike> interfaces;
    public final PVector<NewField> fields;
    public final PVector<NewMethod> methods;
    public final PVector<Instructions> clinitInstructions;

    public static NewClass newClass(ClassLike clazz) {
        return newClass(clazz, setOf(PUBLIC, FINAL));
    }

    public static NewClass newClass(ClassLike clazz, PSet<AccessFlag> flags) {
        return new NewClass(clazz, flags, ClassLike.fromClass(Object.class), Empty.set(), Empty.vector(), Empty.vector(), Empty.vector());
    }

    private NewClass(ClassLike clazz, PSet<AccessFlag> flags, ClassLike superClass, PSet<ClassLike> interfaces, PVector<NewField> fields, PVector<NewMethod> methods, PVector<Instructions> clinitInstructions) {
        this.clazz = clazz;
        this.flags = flags;
        this.superClass = superClass;
        this.interfaces = interfaces;
        this.fields = fields;
        this.methods = methods;
        this.clinitInstructions = clinitInstructions;
    }

    public NewClass withMethod(NewMethod method) {
        return new NewClass(clazz, flags, superClass, interfaces, fields, methods.plus(method), clinitInstructions);
    }

    public NewClass withField(NewField field) {
        return new NewClass(clazz, flags, superClass, interfaces, fields.plus(field), methods, clinitInstructions);
    }

    public NewClass withSuperClass(ClassLike superClass) {
        return new NewClass(clazz, flags, superClass, interfaces, fields, methods, clinitInstructions);
    }

    public NewClass withInterface(ClassLike interfaceClass) {
        return new NewClass(clazz, flags, superClass, interfaces.plus(interfaceClass), fields, methods, clinitInstructions);
    }

    public NewClass withClinitInstructions(Instructions instructions) {
        return new NewClass(clazz, flags, superClass, interfaces, fields, methods, clinitInstructions.plus(instructions));
    }

    private static class Loader extends ClassLoader {
        static {
            registerAsParallelCapable();
        }

        Class<?> defineClass(String name, byte[] bytes) {
            return super.defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static final Loader LOADER = new Loader();

    private static void writeClassFile(String name, byte[] bytes) {
        try {
            Path path = Paths.get("target", "bytecode", String.format("%s.class", name));
            Files.createDirectories(path.getParent());

            try (OutputStream stream = Files.newOutputStream(path)) {
                stream.write(bytes);
            }
        } catch (IOException e) {
            throw panic(e, "Can't write class file");
        }
    }

    public Class<?> defineClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cw.visit(V1_8, AccessFlag.toInt(flags), clazz.getInternalName(), null, superClass.getInternalName(),
            interfaces.stream().map(ClassLike::getInternalName).toArray(String[]::new));

        for (NewField field : fields) {
            FieldVisitor fv = cw.visitField(AccessFlag.toInt(field.flags), field.name, field.clazz.getDescriptor(), null, null);
            fv.visitEnd();
        }

        PVector<NewMethod> methods = this.methods;

        if (!clinitInstructions.isEmpty()) {
            methods = methods.plus(newMethod(setOf(PUBLIC, STATIC), "<clinit>", Void.TYPE, Empty.vector(), mplus(clinitInstructions)));
        }

        for (NewMethod method : methods) {
            MethodVisitor mv = cw.visitMethod(
                AccessFlag.toInt(method.flags),
                method.name,
                Type.getMethodDescriptor(
                    Type.getType(method.returnType),
                    method.parameterTypes.stream().map(Type::getType).toArray(Type[]::new)),
                null,
                null);

            method.instructions.apply(mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }


        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        writeClassFile(clazz.getName(), bytes);
        return LOADER.defineClass(clazz.getName(), bytes);
    }
}
