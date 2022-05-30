package me.itzg.helpers.singles;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static com.github.stefanbirkner.systemlambda.SystemLambda.withTextFromSystemIn;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class HashCommandTest {

  @Test
  void simple() throws Exception {
    final String output = tapSystemOut(() ->
        withTextFromSystemIn("testing").execute(() -> {
          final int exitCode = new CommandLine(new HashCommand()).execute();
          assertThat(exitCode).isEqualTo(0);
        })
    );

    assertThat(output).satisfiesAnyOf(
        // on Linux with \n
        s -> assertThat(s).isEqualToIgnoringNewLines("eb1a3227cdc3fedbaec2fe38bf6c044a"),
        // on Windows with \r\n
        s -> assertThat(s).isEqualToIgnoringNewLines("92940b605f0952c4e67b7b8c000cee17")
    );
  }
}