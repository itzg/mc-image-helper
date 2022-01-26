package me.itzg.helpers.assertcmd;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class PropertyEqualsTest {

  @Test
  void successOnMatch(@TempDir Path tempDir) throws IOException {
    final Path propertyFile = Files.write(tempDir.resolve("testing.properties"),
        Arrays.asList(
            "group.key1=one",
            "group.key2=two"
        )
    );

    final int exitCode = new CommandLine(new PropertyEquals())
        .execute(
            "--file=" + propertyFile,
            "--property=group.key2",
            "--expect=two"
        );

    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void failOnMissingFile(@TempDir Path tempDir) throws Exception {
    final Path absentFile = tempDir.resolve("absent.properties");

    final String errOut = tapSystemErrNormalized(() -> {
      final int exitCode = new CommandLine(new PropertyEquals())
          .execute(
              "--file=" + absentFile,
              "--property=any",
              "--expect=any"
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).isEqualTo("The file " + absentFile
        + " does not exist\n");
  }

  @Test
  void failOnMissingProperty(@TempDir Path tempDir) throws Exception {
    final Path propertyFile = Files.write(tempDir.resolve("testing.properties"),
        Arrays.asList(
            "group.key1=one",
            "group.key2=two"
        )
    );

    final String errOut = tapSystemErrNormalized(() -> {
      final int exitCode = new CommandLine(new PropertyEquals())
          .execute(
              "--file=" + propertyFile,
              "--property=other",
              "--expect=any"
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).isEqualTo("The property other in the file " + propertyFile
        + " does not exist\n");
  }

  @Test
  void failOnMismatchProperty(@TempDir Path tempDir) throws Exception {
    final Path propertyFile = Files.write(tempDir.resolve("testing.properties"),
        Arrays.asList(
            "group.key1=one",
            "group.key2=two"
        )
    );

    final String errOut = tapSystemErrNormalized(() -> {
      final int exitCode = new CommandLine(new PropertyEquals())
          .execute(
              "--file=" + propertyFile,
              "--property=group.key1",
              "--expect=any"
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).isEqualTo("Expected the property group.key1 in the file " + propertyFile
        + " to be 'any', but was 'one'\n");
  }

}