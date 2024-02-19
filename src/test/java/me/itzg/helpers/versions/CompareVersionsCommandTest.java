package me.itzg.helpers.versions;

import static org.assertj.core.api.Assertions.assertThat;

import me.itzg.helpers.LatchingExecutionExceptionHandler;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class CompareVersionsCommandTest {

    @Test
    void noArgs() {
        final int status =
            new CommandLine(new CompareVersionsCommand())
                .execute();

        assertThat(status).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
        "1.18,   lt, 1.18.1, 0",
        "1.18.1, lt, 1.18.1, 1",
        "1.12.1, lt, 1.12.2, 0",
        "b1.7.3, lt, 1.18, 0",
        "b1.7.3, lt, b1.6, 1",
        "a1.4, lt, b1.7.3, 0",
        "1.18, lt, a1.4, 1",
        "1.18.1-rc3, lt, 1.18.1, 0",
        "21w44a, lt, 1.18.1, 1",
        "21w44a, lt, 1.18.1, 1",
    })
    void combinations(String left, String op, String right, int expected) {
        final int status =
            new CommandLine(new CompareVersionsCommand())
                .execute(
                    left, op, right
                );

        assertThat(status).isEqualTo(expected);
    }

    @Test
    void failsUsageWithBlankVersion() {
        final LatchingExecutionExceptionHandler executionExceptionHandler = new LatchingExecutionExceptionHandler();
        final int status =
            new CommandLine(new CompareVersionsCommand())
                .setExecutionExceptionHandler(executionExceptionHandler)
                .execute(
                    "", "lt", "1.16.5"
                );

        assertThat(status).isNotEqualTo(ExitCode.OK);
        assertThat(executionExceptionHandler.getExecutionException())
            .isInstanceOf(InvalidParameterException.class);
    }
}