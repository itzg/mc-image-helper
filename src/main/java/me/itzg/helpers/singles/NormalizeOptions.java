package me.itzg.helpers.singles;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class NormalizeOptions {

    public static List<String> normalizeOptionList(@Nullable List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }

        return normalizeStream(values.stream())
            .collect(Collectors.toList());
    }

    public static Set<String> normalizeOptionSet(@Nullable Set<String> values) {
        if (values == null) {
            return Collections.emptySet();
        }

        return normalizeStream(values.stream())
            .collect(Collectors.toSet());
    }

    public static Stream<String> normalizeStream(Stream<String> values) {
        return values
            .map(s -> {
                final int pos = s.indexOf("#");
                return pos < 0 ? s : s.substring(0, pos);
            })
            .map(String::trim)
            .filter(s -> !s.isEmpty());
    }
}
