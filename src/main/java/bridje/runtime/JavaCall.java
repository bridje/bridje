package bridje.runtime;

import bridje.types.Type;
import org.pcollections.PVector;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static bridje.Util.toPSet;
import static bridje.Util.toPVector;

public abstract class JavaCall {

    public interface JavaCallVisitor<T> {
        T visit(StaticMethodCall call);
    }

    public abstract <T> T accept(JavaCallVisitor<T> visitor);

    public static final class StaticMethodCall extends JavaCall {

        public final Class<?> clazz;
        public final String name;
        public final MethodType methodType;

        public static Optional<StaticMethodCall> find(Class<?> clazz, String name, Type type) {
            MethodType methodType = type.methodType();
            PVector<Method> methods = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(name))
                .filter(m -> m.getParameterCount() == methodType.parameterCount())
                // TODO filter on types too
                .collect(toPVector());

            if (methods.size() == 1) {
                Method method = methods.get(0);
                return Optional.of(new StaticMethodCall(clazz, name, MethodType.methodType(method.getReturnType(), method.getParameterTypes())));
            } else {
                return Optional.empty();
            }
        }

        public StaticMethodCall(Class<?> clazz, String name, MethodType methodType) {
            Arrays.stream(clazz.getMethods()).filter(m -> m.getName().equals(name)).collect(toPSet());
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

        @Override
        public String toString() {
            return String.format("(StaticMethodCall %s/%s (%s))", clazz.getName(), name, methodType);
        }
    }

}
