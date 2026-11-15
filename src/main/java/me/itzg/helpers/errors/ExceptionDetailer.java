package me.itzg.helpers.errors;

import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ExceptionDetailer {

    public static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        return ExceptionUtils.getThrowableList(throwable).stream()
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst()
            .orElse(null);
    }

    public static String buildCausalMessages(Throwable throwable) {
        return ExceptionUtils.getThrowableList(throwable).stream()
            .map(cause -> cause.getClass().getSimpleName() + ": " + cause.getMessage())
            .collect(Collectors.joining("; "));
    }
}
