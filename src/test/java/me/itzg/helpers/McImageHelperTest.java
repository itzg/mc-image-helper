package me.itzg.helpers;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class McImageHelperTest {

    @Test
    void showAllSubcommandUsageRecursesIntoSubSubcommands() throws Exception {
        final String output = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new McImageHelper())
                .execute("show-all-subcommand-usage");

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        final List<String> headings = output.lines()
            .filter(line -> line.startsWith("### "))
            .collect(Collectors.toList());

        // top-level command that itself declares sub-subcommands
        assertThat(headings).contains("### archive");

        // previously, archive's own subcommands were only summarized in archive's
        // "Commands:" usage block; they should now get their own rendered section
        assertThat(headings).contains("### archive check-path-traversal", "### archive extract");

        // recursion should apply generally, not just to "archive"
        assertThat(headings).contains(
            "### assert fileExists",
            "### github download-latest-asset",
            "### install-curseforge schemas"
        );

        // the nested section should render that subcommand's own usage, qualified
        // with its ancestors, not just repeat the parent's usage
        assertThat(output).contains("Usage: mc-image-helper archive extract");
    }
}
