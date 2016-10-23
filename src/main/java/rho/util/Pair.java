package rho.util;

import java.util.Objects;

public class Pair<L, R> {
    public final L left;
    public final R right;

    public static <L, R> Pair<L, R> pair(L left, R right) {
        return new Pair<L, R>(left, right);
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
