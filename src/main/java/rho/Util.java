package rho;

import org.pcollections.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class Util {
    private static final AtomicLong UNIQUE_INTS = new AtomicLong(1000);

    public static long uniqueInt() {
        return UNIQUE_INTS.getAndIncrement();
    }

    public static <T> PSet<T> setOf(T... ts) {
        return HashTreePSet.from(Arrays.asList(ts));
    }

    public static <T> PVector<T> vectorOf(T... ts) {
        return TreePVector.from(Arrays.asList(ts));
    }

    public static <T> Collector<T, List<T>, PVector<T>> toPVector() {
        return new Collector<T, List<T>, PVector<T>>() {
            @Override
            public Supplier<List<T>> supplier() {
                return LinkedList::new;
            }

            @Override
            public BiConsumer<List<T>, T> accumulator() {
                return List::add;
            }

            @Override
            public BinaryOperator<List<T>> combiner() {
                return (left, right) -> {
                    left.addAll(right);
                    return left;
                };
            }

            @Override
            public Function<List<T>, PVector<T>> finisher() {
                return TreePVector::from;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Empty.set();
            }
        };
    }

    public static <T> Collector<T, Set<T>, PSet<T>> toPSet() {
        return new Collector<T, Set<T>, PSet<T>>() {
            @Override
            public Supplier<Set<T>> supplier() {
                return HashSet::new;
            }

            @Override
            public BiConsumer<Set<T>, T> accumulator() {
                return Set::add;
            }

            @Override
            public BinaryOperator<Set<T>> combiner() {
                return (left, right) -> {
                    left.addAll(right);
                    return left;
                };
            }

            @Override
            public Function<Set<T>, PSet<T>> finisher() {
                return HashTreePSet::from;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Empty.set();
            }
        };
    }

    public static <T, K, V> Collector<T, Map<K, V>, PMap<K, V>> toPMap(Function<T, K> keyMapper, Function<T, V> valueMapper) {
        return new Collector<T, Map<K, V>, PMap<K, V>>() {
            @Override
            public Supplier<Map<K, V>> supplier() {
                return HashMap::new;
            }

            @Override
            public BiConsumer<Map<K, V>, T> accumulator() {
                return (m, e) -> m.put(keyMapper.apply(e), valueMapper.apply(e));
            }

            @Override
            public BinaryOperator<Map<K, V>> combiner() {
                return (left, right) -> {
                    left.putAll(right);
                    return left;
                };
            }

            @Override
            public Function<Map<K, V>, PMap<K, V>> finisher() {
                return HashTreePMap::from;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Empty.set();
            }
        };
    }

    public static String toInternalName(String name) {
        return name.replace('.', '/');
    }
}
