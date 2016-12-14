package bridje.runtime;

import bridje.types.Type;

import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.Optional;

public abstract class JavaCall {

    interface JavaCallVisitor<T> {
        T visit(StaticMethodCall call);
    }

    public abstract <T> T accept(JavaCallVisitor<T> visitor);


    public static final class StaticMethodCall extends JavaCall {

        public final Class<?> clazz;
        public final String name;
        public final MethodType methodType;

        public static Optional<StaticMethodCall> find(Class<?> clazz, String name, Type type) {
            throw new UnsupportedOperationException();
        }

        public StaticMethodCall(Class<?> clazz, String name, MethodType methodType) {
            this.clazz = clazz;
            this.name = name;
            this.methodType = methodType;
        }

        @Override
        public <T> T accept(JavaCallVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StaticMethodCall that = (StaticMethodCall) o;
            return Objects.equals(clazz, that.clazz) &&
                Objects.equals(name, that.name) &&
                Objects.equals(methodType, that.methodType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, name, methodType);
        }
    }

}
