package rho.util;

import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Pair<L, R> {
    public final L left;
    public final R right;

    public static <L, R> Pair<L, R> pair(L left, R right) {
        return new Pair<>(left, right);
    }

    public static <L, R> PSequence<Pair<L, R>> zip(PSequence<L> lefts, PSequence<R> rights) {

        List<Pair<L, R>> zipped = new LinkedList<>();

        if (lefts.size() != rights.size()) {
            throw new IllegalArgumentException();
        } else {
            for (int i = 0; i < lefts.size(); i++) {
                zipped.add(pair(lefts.get(i), rights.get(i)));
            }
        }

        return TreePVector.from(zipped);
    }

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(left, pair.left) &&
            Objects.equals(right, pair.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return String.format("(Pair %s %s)", left, right);
    }


}
