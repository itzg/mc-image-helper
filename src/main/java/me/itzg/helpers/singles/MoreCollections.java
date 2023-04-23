package me.itzg.helpers.singles;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MoreCollections {

    private MoreCollections() {
    }

    @SuppressWarnings("unused")
    public static <T> Set<T> makeNonNull(Set<T> in) {
        return in != null ? in : Collections.emptySet();
    }

    @SuppressWarnings("unused")
    public static <T> Set<T> combine(Set<T> in1, Set<T> in2) {
        return Stream.of(in1, in2)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * @return stream from collection if non-null, otherwise an empty stream
     */
    public static <T> Stream<T> safeStreamFrom(Collection<T> collection) {
        return collection != null ? collection.stream() : Stream.empty();
    }
    public static <T> Stream<T> safeStreamFrom(Set<T> set) {
        return set != null ? set.stream() : Stream.empty();
    }

    public static <T> boolean equalsIgnoreOrder(Collection<T> lhs, Collection<T> rhs) {
        if (lhs == null && rhs == null) {
            return true;
        }
        if (lhs == null || rhs == null) {
            return false;
        }
        return lhs.size() == rhs.size()
            && lhs.containsAll(rhs);
    }
}
