package me.itzg.helpers;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static me.itzg.helpers.ShowAllSubcommandUsage.OVERVIEW_END;
import static me.itzg.helpers.ShowAllSubcommandUsage.OVERVIEW_START;
import static me.itzg.helpers.ShowAllSubcommandUsage.SUBCOMMANDS_END;
import static me.itzg.helpers.ShowAllSubcommandUsage.SUBCOMMANDS_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import me.itzg.helpers.errors.ExitCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

class ShowAllSubcommandUsageTest {

    private static final String PREFIX = "# README — café\n\nHandwritten introduction.\n\n";
    private static final String BETWEEN = "\n\nHandwritten [schema links](#schemas).\n\n";
    private static final String SUFFIX = "\n\n## Schemas\nKeep $values and C:\\paths.";
    private static final String README = PREFIX + OVERVIEW_START + "\nold overview\n" + OVERVIEW_END
        + BETWEEN + SUBCOMMANDS_START + "\nold subcommands\n" + SUBCOMMANDS_END + SUFFIX;

    @TempDir
    Path tempDir;

    private CommandLine commandLine;
    private Path readme;

    @BeforeEach
    void setUp() throws IOException {
        readme = tempDir.resolve("README with spaces.md");
        Files.writeString(readme, README);
        commandLine = new CommandLine(new McImageHelper())
            .setExitCodeExceptionMapper(new ExitCodeMapper());
    }

    @Test
    void updatesBothSectionsAndPreservesSurroundingContent() throws IOException {
        final String crlfPrefix = PREFIX.replace("\n", "\r\n");
        Files.writeString(readme, README.replace(PREFIX, crlfPrefix));

        assertThat(commandLine.execute("show-all-subcommand-usage", "update-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);

        final String updated = Files.readString(readme);
        assertThat(updated)
            .startsWith(crlfPrefix + OVERVIEW_START)
            .contains(OVERVIEW_END + BETWEEN + SUBCOMMANDS_START)
            .endsWith(SUBCOMMANDS_END + SUFFIX)
            .contains(OVERVIEW_START + "\n\n```\nUsage: mc-image-helper")
            .contains(SUBCOMMANDS_START + "\n\n### ")
            .contains("### show-all-subcommand-usage\n", "update-readme", "check-readme")
            .doesNotContain("old overview", "old subcommands", "\u001B[");
        assertThat(updated.substring(updated.indexOf(OVERVIEW_START) + OVERVIEW_START.length(),
            updated.indexOf(OVERVIEW_END))).doesNotContain("\r").endsWith("```\n\n");
        assertThat(updated.substring(updated.indexOf(SUBCOMMANDS_START) + SUBCOMMANDS_START.length(),
            updated.indexOf(SUBCOMMANDS_END))).doesNotContain("\r").endsWith("```\n\n");

        assertThat(commandLine.execute("show-all-subcommand-usage", "update-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
        assertThat(Files.readString(readme)).isEqualTo(updated);
        assertThat(commandLine.execute("show-all-subcommand-usage", "check-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
    }

    @ParameterizedTest
    @MethodSource("generatedRegions")
    void rejectsCrlfInEitherGeneratedSectionAndUpdatesOnlyThatSection(String startMarker, String endMarker) throws IOException {
        assertThat(commandLine.execute("show-all-subcommand-usage", "update-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
        final String updated = Files.readString(readme);
        final int start = updated.indexOf(startMarker) + startMarker.length();
        final int end = updated.indexOf(endMarker);
        final String withCrlf = updated.substring(0, start)
            + updated.substring(start, end).replace("\n", "\r\n")
            + updated.substring(end);
        Files.writeString(readme, withCrlf);

        assertThat(commandLine.execute("show-all-subcommand-usage", "check-readme", readme.toString()))
            .isEqualTo(ExitCode.SOFTWARE);
        assertThat(Files.readString(readme)).isEqualTo(withCrlf);
        assertThat(commandLine.execute("show-all-subcommand-usage", "update-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
        assertThat(Files.readString(readme)).isEqualTo(updated);
        assertThat(commandLine.execute("show-all-subcommand-usage", "check-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
    }

    static Stream<Arguments> generatedRegions() {
        return Stream.of(
            arguments(OVERVIEW_START, OVERVIEW_END),
            arguments(SUBCOMMANDS_START, SUBCOMMANDS_END));
    }

    @Test
    void detectsNewCommandWithoutWritingAndPassesAfterUpdate() throws Exception {
        assertThat(commandLine.execute("show-all-subcommand-usage", "update-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
        assertThat(commandLine.execute("show-all-subcommand-usage", "check-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
        final String before = Files.readString(readme);

        commandLine.addSubcommand("new-command", CommandSpec.create().name("new-command"));

        final String stderr = tapSystemErr(() ->
            assertThat(commandLine.execute("show-all-subcommand-usage", "check-readme", readme.toString()))
                .isEqualTo(ExitCode.SOFTWARE));
        assertThat(stderr)
            .contains("is out of date", "mc-image-helper show-all-subcommand-usage update-readme \"" + readme + "\"");
        assertThat(Files.readString(readme)).isEqualTo(before);
        assertThat(commandLine.execute("show-all-subcommand-usage", "update-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
        assertThat(Files.readString(readme)).contains("### new-command");
        assertThat(commandLine.execute("show-all-subcommand-usage", "check-readme", readme.toString()))
            .isEqualTo(ExitCode.OK);
    }

    @ParameterizedTest
    @MethodSource("malformedReadmes")
    void rejectsMalformedMarkersWithoutWriting(String content) throws Exception {
        Files.writeString(readme, content);

        tapSystemErr(() ->
            assertThat(commandLine.execute("show-all-subcommand-usage", "update-readme", readme.toString()))
                .isEqualTo(ExitCode.USAGE));
        assertThat(Files.readString(readme)).isEqualTo(content);
    }

    static Stream<String> malformedReadmes() {
        return Stream.of(
            README.replace(SUBCOMMANDS_END, ""),
            README + OVERVIEW_START,
            OVERVIEW_START + SUBCOMMANDS_START + OVERVIEW_END + SUBCOMMANDS_END);
    }

}
