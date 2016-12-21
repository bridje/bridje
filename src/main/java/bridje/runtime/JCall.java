package bridje.runtime;

import bridje.types.Type;
import bridje.util.Pair;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.lang.reflect.Modifier;
import java.util.*;

import static bridje.Util.toPVector;
import static bridje.util.Pair.zip;
import static java.util.stream.Collectors.joining;

public abstract class JCall {

    public interface JCallVisitor<T> {
        T visit(StaticMethodCall call);

        T visit(InstanceMethodCall call);
    }

    public final JSignature signature;

    JCall(JSignature signature) {
        this.signature = signature;
    }

    public abstract <T> T accept(JCallVisitor<T> visitor);

    public static class NoMatches extends Exception {

    }

    public static class MultipleMatches extends Exception {
        public final PVector<JSignature> matches;

        public MultipleMatches(PVector<JSignature> matches) {
            this.matches = matches;
        }
    }

    public static final class StaticMethodCall extends JCall {

        public final Class<?> clazz;
        public final String name;

        public static StaticMethodCall find(Class<?> clazz, String name, Type type) throws NoMatches, MultipleMatches {
            JSignature signature = type.javaSignature();

            PVector<StaticMethodCall> matches = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(name))
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .map(m ->
                    signature.match(TreePVector.from(Arrays.asList(m.getParameterTypes())), m.getReturnType())
                        .map(sig -> new StaticMethodCall(clazz, name, sig))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(toPVector());

            switch (matches.size()) {
                case 1:
                    return matches.get(0);
                case 0:
                    throw new NoMatches();
                default:
                    throw new MultipleMatches(matches.stream()
                        .map(match -> match.signature)
                        .collect(toPVector()));
            }
        }

        public StaticMethodCall(Class<?> clazz, String name, JSignature signature) {
            super(signature);
            this.clazz = clazz;
            this.name = name;
        }

        @Override
        public <T> T accept(JCallVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StaticMethodCall that = (StaticMethodCall) o;
            return Objects.equals(clazz, that.clazz) &&
                Objects.equals(name, that.name) &&
                Objects.equals(signature, that.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, name, signature);
        }

        @Override
        public String toString() {
            return String.format("(StaticMethodCall %s/%s %s", clazz.getName(), name, signature);
        }
    }

    public static class InstanceMethodCall extends JCall {
        public final Class<?> clazz;
        public final String name;

        public static InstanceMethodCall find(Class<?> clazz, String name, Type type) throws NoMatches, MultipleMatches {
            if (!(type instanceof Type.FnType)) {
                throw new NoMatches();
            }

            Type.FnType fnType = (Type.FnType) type;

            JSignature fnSignature = fnType.javaSignature();
            if (fnSignature.jParams.isEmpty()) {
                JSignature.JParam thisParam = fnSignature.jParams.get(0);
                if (!thisParam.paramClass.equals(clazz)) {
                    throw new NoMatches();
                }
            }

            JSignature signature = new JSignature(fnSignature.jParams.minus(0), fnSignature.jReturn);

            PVector<InstanceMethodCall> matches = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(name))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .map(m ->
                    signature.match(TreePVector.from(Arrays.asList(m.getParameterTypes())), m.getReturnType())
                        .map(sig -> new InstanceMethodCall(clazz, name, sig))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(toPVector());

            switch (matches.size()) {
                case 1:
                    return matches.get(0);
                case 0:
                    throw new NoMatches();
                default:
                    throw new MultipleMatches(matches.stream()
                        .map(match -> match.signature)
                        .collect(toPVector()));
            }

        }

        public InstanceMethodCall(Class<?> clazz, String name, JSignature signature) {
            super(signature);
            this.clazz = clazz;
            this.name = name;
        }

        @Override
        public <T> T accept(JCallVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InstanceMethodCall that = (InstanceMethodCall) o;
            return Objects.equals(clazz, that.clazz) &&
                Objects.equals(name, that.name) &&
                Objects.equals(signature, that.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, name, signature);
        }

        @Override
        public String toString() {
            return String.format("(InstanceMethodCall %s/%s %s", clazz.getName(), name, signature);
        }
    }
}
