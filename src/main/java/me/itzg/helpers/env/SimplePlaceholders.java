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
        final Matcher m = PLACEHOLDERS_PATTERN.matcher(value);
        if (m.find()) {
            final StringBuffer sb = new StringBuffer();
            do {
                final String type = Optional.ofNullable(m.group("type"))
                    .orElse("env");
                final String replacement = buildPlaceholderReplacement(type, m.group("var"), m.group());

                m.appendReplacement(sb, replacement);
            } while (m.find());

            m.appendTail(sb);
            return sb.toString();
        }
        return value;
    }

    private String buildPlaceholderReplacement(String type, String var, String fallback) {
        log.debug("Building placeholder replacement from type={} with var='{}'", type, var);
        switch (type) {
            case "env":
                final String result = environmentVariablesProvider.get(var);
                if (result != null) {
                    return result;
                }
                else {
                    log.warn("Unable to resolve environment variable {}", var);
                    return fallback;
                }

            case "date":
            case "time":
                try {
                    final DateTimeFormatter f = DateTimeFormatter.ofPattern(var);
                    return ZonedDateTime.now(clock).format(f);
                } catch (IllegalArgumentException e) {
                    log.error("Invalid date/time format in {}", var, e);
                    return fallback;
                }
        }

        return fallback;
    }

}
