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

public abstract class JavaCall {

    public interface JavaCallVisitor<T> {
        T visit(StaticMethodCall call);

        T visit(InstanceMethodCall call);
    }

    public abstract <T> T accept(JavaCallVisitor<T> visitor);

    public static class NoMatches extends Exception {

    }

    public static class MultipleMatches extends Exception {
        public final PVector<JavaSignature> matches;

        public MultipleMatches(PVector<JavaSignature> matches) {
            this.matches = matches;
        }
    }

    public static class JavaParam {
        public final Class<?> paramClass;

        public JavaParam(Class<?> paramClass) {
            this.paramClass = paramClass;
        }

        public Optional<JavaParam> match(Class<?> methodParamClass) {
            if (methodParamClass.isAssignableFrom(paramClass)) {
                return Optional.of(new JavaParam(methodParamClass));
            }

            return Optional.empty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JavaParam javaParam = (JavaParam) o;
            return Objects.equals(paramClass, javaParam.paramClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(paramClass);
        }

        @Override
        public String toString() {
            return paramClass.getName();
        }
    }

    public static class JavaReturn {
        public enum ReturnWrapper {
            IO
        }

        public final Class<?> returnClass;
        public final PVector<ReturnWrapper> wrappers;

        public JavaReturn(Class<?> returnClass, PVector<ReturnWrapper> wrappers) {
            this.returnClass = returnClass;
            this.wrappers = wrappers;
        }

        public Optional<JavaReturn> match(Class<?> methodReturn) {
            if (returnClass.isAssignableFrom(methodReturn)) {
                return Optional.of(new JavaReturn(methodReturn, wrappers));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JavaReturn that = (JavaReturn) o;
            return Objects.equals(returnClass, that.returnClass) &&
                Objects.equals(wrappers, that.wrappers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(returnClass, wrappers);
        }

        @Override
        public String toString() {
            return returnClass.getName();
        }
    }

    public static class JavaSignature {
        public final PVector<JavaParam> javaParams;
        public final JavaReturn javaReturn;

        public JavaSignature(PVector<JavaParam> javaParams, JavaReturn javaReturn) {
            this.javaParams = javaParams;
            this.javaReturn = javaReturn;
        }

        public Optional<JavaSignature> match(PVector<Class<?>> methodParams, Class<?> methodReturn) {
            if (javaParams.size() != methodParams.size()) {
                return Optional.empty();
            }

            List<JavaParam> matchedParams = new LinkedList<>();
            for (Pair<JavaParam, Class<?>> paramPair : zip(javaParams, methodParams)) {
                Optional<JavaParam> matchedParam = paramPair.left.match(paramPair.right);
                if (matchedParam.isPresent()) {
                    matchedParams.add(matchedParam.get());
                } else {
                    return Optional.empty();
                }
            }

            Optional<JavaReturn> matchedReturn = javaReturn.match(methodReturn);
            if (matchedReturn.isPresent()) {
                return Optional.of(new JavaSignature(TreePVector.from(matchedParams), matchedReturn.get()));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JavaSignature signature = (JavaSignature) o;
            return Objects.equals(javaParams, signature.javaParams) &&
                Objects.equals(javaReturn, signature.javaReturn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(javaParams, javaReturn);
        }

        @Override
        public String toString() {
            return String.format("(JavaSignature %s)",
                javaParams.isEmpty()
                    ? javaReturn
                    : String.format("(-> %s %s)", javaParams.stream().map(Object::toString).collect(joining(" ")), javaReturn));
        }
    }

    public static final class StaticMethodCall extends JavaCall {

        public final Class<?> clazz;
        public final String name;
        public final JavaSignature signature;

        public static StaticMethodCall find(Class<?> clazz, String name, Type type) throws NoMatches, MultipleMatches {
            JavaSignature signature = type.javaSignature();

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

        public StaticMethodCall(Class<?> clazz, String name, JavaSignature signature) {
            this.clazz = clazz;
            this.name = name;
            this.signature = signature;
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

    public static class InstanceMethodCall extends JavaCall {
        public final Class<?> clazz;
        public final String name;
        public final JavaSignature signature;

        public static InstanceMethodCall find(Class<?> clazz, String name, Type type) throws NoMatches, MultipleMatches {
            if (!(type instanceof Type.FnType)) {
                throw new NoMatches();
            }

            Type.FnType fnType = (Type.FnType) type;

            JavaSignature fnSignature = fnType.javaSignature();
            if (fnSignature.javaParams.isEmpty()) {
                JavaParam thisParam = fnSignature.javaParams.get(0);
                if (!thisParam.paramClass.equals(clazz)) {
                    throw new NoMatches();
                }
            }

            JavaSignature signature = new JavaSignature(fnSignature.javaParams.minus(0), fnSignature.javaReturn);

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

        public InstanceMethodCall(Class<?> clazz, String name, JavaSignature signature) {
            this.clazz = clazz;
            this.name = name;
            this.signature = signature;
        }

        @Override
        public <T> T accept(JavaCallVisitor<T> visitor) {
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
