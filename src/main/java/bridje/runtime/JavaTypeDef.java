package bridje.runtime;

import bridje.types.Type;

import java.util.Objects;

public class JavaTypeDef {

    public final JavaCall javaCall;
    public final Type type;

    public JavaTypeDef(JavaCall javaCall, Type type) {
        this.javaCall = javaCall;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaTypeDef that = (JavaTypeDef) o;
        return Objects.equals(javaCall, that.javaCall) &&
            Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaCall, type);
    }
}
