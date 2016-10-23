package rho;

import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.util.Arrays;

public class Util {

    public static <T> PSet<T> setOf(T... ts) {
        return HashTreePSet.from(Arrays.asList(ts));
    }

    public static <T> PVector<T> vectorOf(T... ts) {
        return TreePVector.from(Arrays.asList(ts));
    }
}
