package me.itzg.helpers.assertcmd;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class JsonPathEqualsTest {

  @Test
  void passesForString(@TempDir Path tempDir) throws IOException {
    final Path opsJson = Files.write(tempDir.resolve("ops.json"),
        Collections.singletonList(
            "[ { \"uuid\": \"1-2-3-4\", \"name\": \"itzg\", \"level\": 4 } ]"
        )
    );

    final int exitCode = new CommandLine(new JsonPathEquals())
        .execute(
            "--file=" + opsJson,
            "--path=$[0].name",
            "--expect=itzg"
        );

    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void passesForNumber(@TempDir Path tempDir) throws IOException {
    final Path opsJson = Files.write(tempDir.resolve("ops.json"),
        Collections.singletonList(
            "[ { \"uuid\": \"1-2-3-4\", \"name\": \"itzg\", \"level\": 4 } ]"
        )
    );

    final int exitCode = new CommandLine(new JsonPathEquals())
        .execute(
            "--file=" + opsJson,
            "--path=$[0].level",
            "--expect=4"
        );

    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void failsForMismatch(@TempDir Path tempDir) throws Exception {
    final Path opsJson = Files.write(tempDir.resolve("ops.json"),
        Collections.singletonList(
            "[ { \"uuid\": null, \"name\": \"itzg\", \"level\": 4 } ]"
        )
    );

    final String errOut = tapSystemErrNormalized(() -> {
      final int exitCode = new CommandLine(new JsonPathEquals())
          .execute(
              "--file=" + opsJson,
              "--path=$[0].name",
              "--expect=notg"
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).isEqualTo("Expected 'notg' at the path $[0].name in " + opsJson
        + ", but was 'itzg'\n");
  }

  @Test
  void failsForMissingField(@TempDir Path tempDir) throws Exception {
    final Path opsJson = Files.write(tempDir.resolve("ops.json"),
        Collections.singletonList(
            "[ { \"uuid\": null, \"name\": \"itzg\", \"level\": 4 } ]"
        )
    );

    final String errOut = tapSystemErrNormalized(() -> {
      final int exitCode = new CommandLine(new JsonPathEquals())
          .execute(
              "--file=" + opsJson,
              "--path=$[0].doesNotExist",
              "--expect=NA"
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).isEqualTo("The path $[0].doesNotExist in " + opsJson
        + " does not exist\n");
  }

  @Test
  void failsForNullField(@TempDir Path tempDir) throws Exception {
    final Path opsJson = Files.write(tempDir.resolve("ops.json"),
        Collections.singletonList(
            "[ { \"uuid\": null, \"name\": \"itzg\", \"level\": 4 } ]"
        )
    );

    final String errOut = tapSystemErrNormalized(() -> {
      final int exitCode = new CommandLine(new JsonPathEquals())
          .execute(
              "--file=" + opsJson,
              "--path=$[0].uuid",
              "--expect=1-2-3-4"
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).isEqualTo("Expected '1-2-3-4' at the path $[0].uuid in " + opsJson
        + ", but was 'null'\n");
  }

  @Test
  void failsForMissingFile(@TempDir Path tempDir) throws Exception {
    final Path missingPath = tempDir.resolve("ops.json");

    final String errOut = tapSystemErrNormalized(() -> {

      final int exitCode = new CommandLine(new JsonPathEquals())
          .execute(
              "--file=" + missingPath,
              "--path=$[0].uuid",
              "--expect=1-2-3-4"
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).isEqualTo("The file " + missingPath + " does not exist\n");
  }
}