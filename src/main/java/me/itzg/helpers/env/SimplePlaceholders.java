package me.itzg.helpers.env;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Performs simple placeholder replacement of <code>%VAR%</code>s
 */
@Slf4j
public class SimplePlaceholders {

    /**
     * Supports
     * <ul>
     *     <li>%VERSION%</li>
     *     <li>%env:VERSION%</li>
     *     <li>%date:YYYY%</li>
     * </ul>
     */
    private static final Pattern PLACEHOLDERS_PATTERN = Pattern.compile("%((?<type>\\w+):)?(?<var>.+?)%");

    private final EnvironmentVariablesProvider environmentVariablesProvider;
    private final Clock clock;

    public SimplePlaceholders(EnvironmentVariablesProvider environmentVariablesProvider, Clock clock) {
        this.environmentVariablesProvider = environmentVariablesProvider;
        this.clock = clock;
    }

    public String processPlaceholders(String value) {
        return processPlaceholders(value, null);
    }

    /**
     * @param redactForProperty property to name in diagnostic logs instead of placeholder text.
     *                          {@code null} keeps the historical messages, which include that text.
     */
    public String processPlaceholders(String value, String redactForProperty) {
        final Matcher m = PLACEHOLDERS_PATTERN.matcher(value);
        if (m.find()) {
            final StringBuffer sb = new StringBuffer();
            do {
                final String type = Optional.ofNullable(m.group("type"))
                    .orElse("env");
                final String replacement = buildPlaceholderReplacement(type, m.group("var"), redactForProperty);

                if (replacement == null) {
                    continue;
                }

                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } while (m.find());

            m.appendTail(sb);
            return sb.toString();
        }
        return value;
    }

    private String buildPlaceholderReplacement(String type, String var, String redactForProperty) {
        if (redactForProperty == null) {
            log.debug("Building placeholder replacement from type={} with var='{}'", type, var);
        }
        else {
            log.debug("Building placeholder replacement for property {}", redactForProperty);
        }
        switch (type) {
            case "env":
                final String result = environmentVariablesProvider.get(var);
                if (result == null) {
                    if (redactForProperty == null) {
                        log.warn("Unable to resolve environment variable {}", var);
                    }
                    else {
                        log.warn("Unable to resolve placeholder in property {}", redactForProperty);
                    }
                }
                return result;

            case "date":
            case "time":
                try {
                    final DateTimeFormatter f = DateTimeFormatter.ofPattern(var);
                    return ZonedDateTime.now(clock).format(f);
                } catch (IllegalArgumentException e) {
                    if (redactForProperty == null) {
                        log.error("Invalid date/time format in {}", var, e);
                    }
                    else {
                        // The formatter exception text includes pattern letters from the value.
                        log.error("Invalid date/time format in property {}", redactForProperty);
                    }
                    return null;
                }
        }

        return null;
    }

}
