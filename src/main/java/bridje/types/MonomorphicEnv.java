package bridje.types;

import bridje.analyser.LocalVar;
import org.pcollections.*;

import java.util.HashMap;
import java.util.Map;

import static bridje.Util.toPMap;
import static bridje.Util.toPVector;

class MonomorphicEnv {
    final PMap<LocalVar, Type> typings;

    static final MonomorphicEnv EMPTY = new MonomorphicEnv(Empty.map());

    MonomorphicEnv(PMap<LocalVar, Type> typings) {
        this.typings = typings;
    }

    MonomorphicEnv(LocalVar localVar, Type.TypeVar typeVar) {
        this.typings = HashTreePMap.singleton(localVar, typeVar);
    }

    MonomorphicEnv minusAll(PCollection<LocalVar> localVars) {
        return new MonomorphicEnv(typings.minusAll(localVars));
    }

    MonomorphicEnv apply(TypeMapping mapping) {
        return new MonomorphicEnv(typings.entrySet().stream()
            .collect(toPMap(Map.Entry::getKey, e -> e.getValue().apply(mapping))));
    }

    static MonomorphicEnv union(PCollection<MonomorphicEnv> envs) {
        final Map<LocalVar, Type> typings = new HashMap<>();

        for (MonomorphicEnv env : envs) {
            for (Map.Entry<LocalVar, Type> envEntry : env.typings.entrySet()) {
                typings.compute(envEntry.getKey(), (lv, t) -> {
                    Type type = envEntry.getValue();
                    if (t == null || t.equals(type)) {
                        return type;
                    } else {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        }

        return new MonomorphicEnv(HashTreePMap.from(typings));
    }

    static PVector<TypeEquation> typeEquations(PCollection<MonomorphicEnv> envs) {
        PMap<LocalVar, Type.TypeVar> localVarTypeVars = envs.stream()
            .flatMap(env -> env.typings.keySet().stream())
            .collect(toPMap(lv -> lv, lv -> new Type.TypeVar()));

        return envs.stream()
            .flatMap(env -> env.typings.entrySet().stream())
            .map(e -> new TypeEquation(localVarTypeVars.get(e.getKey()), e.getValue()))
            .collect(toPVector());
    }
}
