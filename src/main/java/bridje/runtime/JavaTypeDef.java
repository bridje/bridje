package bridje.runtime;

import bridje.types.Type;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

public class JavaTypeDef {

    public final MethodHandle handle;
    public final Type type;

    public JavaTypeDef(MethodHandle handle, Type type) {
        this.handle = handle;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaTypeDef that = (JavaTypeDef) o;
        return Objects.equals(handle, that.handle) &&
            Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handle, type);
    }
}
