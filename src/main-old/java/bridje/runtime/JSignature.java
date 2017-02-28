package bridje.runtime;

import bridje.util.Pair;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static bridje.util.Pair.zip;
import static java.util.stream.Collectors.joining;

public class JSignature {
    public final PVector<JParam> jParams;
    public final JReturn jReturn;

    public JSignature(PVector<JParam> jParams, JReturn jReturn) {
        this.jParams = jParams;
        this.jReturn = jReturn;
    }

    public Optional<JSignature> match(PVector<Class<?>> methodParams, Class<?> methodReturn) {
        if (jParams.size() != methodParams.size()) {
            return Optional.empty();
        }

        List<JParam> matchedParams = new LinkedList<>();
        for (Pair<JParam, Class<?>> paramPair : zip(jParams, methodParams)) {
            Optional<JParam> matchedParam = paramPair.left.match(paramPair.right);
            if (matchedParam.isPresent()) {
                matchedParams.add(matchedParam.get());
            } else {
                return Optional.empty();
            }
        }

        Optional<JReturn> matchedReturn = jReturn.match(methodReturn);
        if (matchedReturn.isPresent()) {
            return Optional.of(new JSignature(TreePVector.from(matchedParams), matchedReturn.get()));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JSignature signature = (JSignature) o;
        return Objects.equals(jParams, signature.jParams) &&
            Objects.equals(jReturn, signature.jReturn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jParams, jReturn);
    }

    @Override
    public String toString() {
        return String.format("(JSignature %s)",
            jParams.isEmpty()
                ? jReturn
                : String.format("(-> %s %s)", jParams.stream().map(Object::toString).collect(joining(" ")), jReturn));
    }

    public static class JParam {
        public final Class<?> paramClass;

        public JParam(Class<?> paramClass) {
            this.paramClass = paramClass;
        }

        public Optional<JParam> match(Class<?> methodParamClass) {
            if (methodParamClass.isAssignableFrom(paramClass)) {
                return Optional.of(new JParam(methodParamClass));
            }

            return Optional.empty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JParam jParam = (JParam) o;
            return Objects.equals(paramClass, jParam.paramClass);
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

    public static class JReturn {
        public enum ReturnWrapper {
            IO
        }

        public final Class<?> returnClass;
        public final PVector<ReturnWrapper> wrappers;

        public JReturn(Class<?> returnClass, PVector<ReturnWrapper> wrappers) {
            this.returnClass = returnClass;
            this.wrappers = wrappers;
        }

        public Optional<JReturn> match(Class<?> methodReturn) {
            if (returnClass.isAssignableFrom(methodReturn)) {
                return Optional.of(new JReturn(methodReturn, wrappers));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JReturn that = (JReturn) o;
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
}
