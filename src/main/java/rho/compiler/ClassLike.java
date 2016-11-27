package rho.compiler;

import org.objectweb.asm.Type;

import static rho.Util.toInternalName;

interface ClassLike {
    String getName();
    String getInternalName();
    String getDescriptor();

    static ClassLike fromClass(Class<?> clazz) {
        return new ClassLike() {
            @Override
            public String getName() {
                return clazz.getName();
            }

            @Override
            public String getInternalName() {
                return Type.getInternalName(clazz);
            }

            @Override
            public String getDescriptor() {
                return Type.getDescriptor(clazz);
            }
        };
    }

    static ClassLike fromClassName(String className) {
        return new ClassLike() {
            @Override
            public String getName() {
                return className;
            }

            @Override
            public String getInternalName() {
                return toInternalName(className);
            }

            @Override
            public String getDescriptor() {
                return String.format("L%s;", getInternalName());
            }
        };
    }
}
