package bridje.types;

import org.pcollections.PCollection;

import java.util.LinkedList;

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

                LinkedList<TypeEquation> mappedTeqs = new LinkedList<>();
                for (TypeEquation teq : teqs) {
                    mappedTeqs.add(new TypeEquation(teq.left.apply(singleMapping), teq.right.apply(singleMapping)));
                }

                mapping = new TypeMapping(mapping.with(singleMapping).mapping.entrySet().stream()
                    .collect(
                        toPMap(
                            e -> e.getKey(),
                            e -> e.getValue().apply(singleMapping))));

                teqs = mappedTeqs;

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

            if (left instanceof Type.FnType || left instanceof Type.DataTypeType) {
                // TODO
                throw new UnsupportedOperationException();
            }

            throw panic("Can't unify types: %s & %s", left, right);
        }

        return mapping;
    }
}
