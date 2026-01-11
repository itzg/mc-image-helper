package me.itzg.helpers.files;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.github.stefanbirkner.systemlambda.SystemLambda.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class IniPathCommandTest {

    public static Stream<Arguments> extractsFromUnsupConfig_args() {
        return Stream.of(
            arguments("/version", "1"),
            arguments("/preset", "minecraft"),
            arguments("flavors/flavor", "standard")
        );
    }

    @ParameterizedTest
    @MethodSource("extractsFromUnsupConfig_args")
    void extractsFromUnsupConfig(String query, String expectedValue) throws Exception {
        final String out = tapSystemOutNormalized(() -> {
            final int exitCode = new CommandLine(new IniPathCommand())
                .execute(
                    "--file", Paths.get("src/test/resources/unsup.ini").toString(),
                    query
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(out).isEqualTo(expectedValue + "\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/no1", "flavors/no3"})
    void errorOnMissingFields(String query) throws Exception {
        final String err = tapSystemErrNormalized(() -> {
            final int exitCode = new CommandLine(new IniPathCommand())
                .execute(
                    "--file", Paths.get("src/test/resources/unsup.ini").toString(),
                    query
                );

            assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        });

        assertThat(err).contains("Field not found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"noglobal", "dangling/", "section/field["})
    void invalidQuerySyntax(String query) throws Exception {
        final String err = tapSystemErrNormalized(() -> {
            final int exitCode = new CommandLine(new IniPathCommand())
                .execute(
                    "--file", Paths.get("src/test/resources/unsup.ini").toString(),
                    query
                );

            assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        });

        assertThat(err).contains("Query expression is invalid");
    }
}