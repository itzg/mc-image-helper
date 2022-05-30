package me.itzg.helpers.singles;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class YamlPathCmdTest {

  @Test
  void pickOutFieldFromServerSetupConfig() throws Exception {
    final String output = tapSystemOut(() -> {
          final int exitCode = new CommandLine(new YamlPathCmd())
              .execute(
                  "--file", "src/test/resources/server-setup-config.yaml",
                  ".install.baseInstallPath"
              );
          assertThat(exitCode).isEqualTo(0);
        }
    );

    assertThat(output).isEqualToIgnoringNewLines("setup/");
  }
}