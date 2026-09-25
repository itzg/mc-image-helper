package me.itzg.helpers;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class McImageHelperTest {

  @Test
  void showAllSubcommandUsageRecursesIntoSubSubcommands() throws Exception {
    final String output = tapSystemOut(() -> {
      final int exitCode = new CommandLine(new McImageHelper())
          .execute("show-all-subcommand-usage");
      assertThat(exitCode).isEqualTo(0);
    });

    // top-level subcommands still rendered as before
    assertThat(output).contains("### archive");

    // nested subcommands are now rendered with deeper headings...
    assertThat(output).contains("#### check-path-traversal");
    assertThat(output).contains("#### extract");

    // ...including their full usage with options
    assertThat(output).contains("Usage: mc-image-helper archive extract");
    assertThat(output).contains("--overwrite");
    assertThat(output).contains("Usage: mc-image-helper archive check-path-traversal");
  }
}
