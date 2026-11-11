package me.itzg.helpers.env;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class SimplePlaceholdersTest {

    @ParameterizedTest
    @MethodSource("literalReplacementValues")
    void resolvedReplacementIsLiteral(String replacement) {
        final SimplePlaceholders placeholders = new SimplePlaceholders(
            MappedEnvVarProvider.of("VALUE", replacement),
            Clock.systemUTC()
        );

        assertThat(placeholders.processPlaceholders("x%VALUE%y"))
            .isEqualTo("x" + replacement + "y");
    }

    static Stream<Arguments> literalReplacementValues() {
        return Stream.of(
            arguments("cost-$name"),
            arguments("$5"),
            arguments("a\\path"),
            arguments("trail\\"),
            arguments("mixed $1 and \\ and ${group}")
        );
    }

    @Test
    void quotesEachResolvedReplacement() {
        final SimplePlaceholders placeholders = new SimplePlaceholders(
            MappedEnvVarProvider.of("A", "cost-$name", "B", "a\\path"),
            Clock.systemUTC()
        );

        assertThat(placeholders.processPlaceholders("%A%:%B%"))
            .isEqualTo("cost-$name:a\\path");
    }

    @Test
    void unresolvedPlaceholderLogsVariableName() {
        final String fragment = "Jjam;$L9adz-tf";
        final SimplePlaceholders placeholders = new SimplePlaceholders(
            MappedEnvVarProvider.of(),
            Clock.systemUTC()
        );

        final List<String> logs = captureLogs(() ->
            assertThat(placeholders.processPlaceholders("x%" + fragment + "%y"))
                .isEqualTo("x%" + fragment + "%y")
        );

        assertThat(String.join("\n", logs))
            .contains("Unable to resolve environment variable " + fragment)
            .contains("Building placeholder replacement from type=env with var='" + fragment + "'");
    }

    private static List<String> captureLogs(Runnable action) {
        final Logger logger = (Logger) LoggerFactory.getLogger(SimplePlaceholders.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        final Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            action.run();
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        return appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
    }
}
