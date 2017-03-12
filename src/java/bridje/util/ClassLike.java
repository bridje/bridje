package bridje.util;

import org.objectweb.asm.Type;

import static bridje.Util.toInternalName;

public interface ClassLike {
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

            @Override
            public String toString() {
                return getName();
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

            @Override
            public String toString() {
                return getName();
            }
        };
    }
}
