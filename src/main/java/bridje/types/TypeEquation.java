package bridje.types;

import org.pcollections.PCollection;

import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import static bridje.Panic.panic;
import static bridje.Util.toPMap;
import static bridje.Util.toPVector;
import static bridje.util.Pair.zip;

class TypeEquation {
    final Type left, right;

    TypeEquation(Type left, Type right) {
        this.left = left;
        this.right = right;
    }

    TypeEquation apply(TypeMapping mapping) {
        return new TypeEquation(left.apply(mapping), right.apply(mapping));
    }

    static PCollection<TypeEquation> apply(PCollection<TypeEquation> equations, TypeMapping mapping) {
        return equations.stream().map(e -> e.apply(mapping)).collect(toPVector());
    }

    static TypeMapping unify(PCollection<TypeEquation> typeEquations) {
        LinkedList<TypeEquation> teqs = new LinkedList<>(typeEquations);
        TypeMapping mapping = TypeMapping.EMPTY;

        while (!teqs.isEmpty()) {
            TypeEquation next = teqs.pop();

            Type left = next.left;
            Type right = next.right;

            if (left.equals(right)) {
                continue;
            }

            if (left instanceof Type.TypeVar) {
                Type.TypeVar typeVar = (Type.TypeVar) left;

                if (right.ftvs().contains(left)) {
                    // This is the 'circular types' case
                    throw new UnsupportedOperationException();
                }

                TypeMapping singleMapping = TypeMapping.singleton(typeVar, right);

                mapping = new TypeMapping(mapping.with(singleMapping).mapping.entrySet().stream().collect(toPMap(Map.Entry::getKey, e -> e.getValue().apply(singleMapping))));

                teqs = teqs.stream()
                    .map(teq -> new TypeEquation(teq.left.apply(singleMapping), teq.right.apply(singleMapping)))
                    .collect(Collectors.toCollection(LinkedList::new));

                continue;
            }

            if (right instanceof Type.TypeVar) {
                teqs.addFirst(new TypeEquation(right, left));
                continue;
            }

            if (left instanceof Type.FnType && right instanceof Type.FnType) {
                Type.FnType leftFn = (Type.FnType) left;
                Type.FnType rightFn = (Type.FnType) right;

                if (leftFn.paramTypes.size() != rightFn.paramTypes.size()) {
                    throw new UnsupportedOperationException();
                } else {
                    teqs.addAll(0,
                        zip(leftFn.paramTypes, rightFn.paramTypes).stream()
                            .map(pts -> new TypeEquation(pts.left, pts.right))
                            .collect(toPVector())
                            .plus(new TypeEquation(leftFn.returnType, rightFn.returnType)));
                    continue;
                }
            }

            if (left instanceof Type.AppliedType && right instanceof Type.AppliedType) {
                Type.AppliedType leftAT = (Type.AppliedType) left;
                Type.AppliedType rightAT = (Type.AppliedType) right;

                if (leftAT.typeParams.size() != rightAT.typeParams.size()) {
                    throw new UnsupportedOperationException();
                } else {
                    teqs.addFirst(new TypeEquation(leftAT.appliedType, rightAT.appliedType));
                    teqs.addAll(0, zip(leftAT.typeParams, rightAT.typeParams).stream().map(tps -> new TypeEquation(tps.left, tps.right)).collect(toPVector()));
                    continue;
                }
            }

            throw panic("Can't unify types: %s & %s", left, right);
        }

        return mapping;
    }
}
