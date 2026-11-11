package me.itzg.helpers.env;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

        final List<ILoggingEvent> events = TestLoggingAppender.captureLogs(SimplePlaceholders.class, () ->
            assertThat(placeholders.processPlaceholders("x%" + fragment + "%y"))
                .isEqualTo("x%" + fragment + "%y")
        );

        final String logs = events.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
        assertThat(logs)
            .contains("Unable to resolve environment variable " + fragment)
            .contains("Building placeholder replacement from type=env with var='" + fragment + "'");
    }
}
