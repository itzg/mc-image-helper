package me.itzg.helpers.assertcmd;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static me.itzg.helpers.MoreAssertions.assertThatLines;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class FileNotExistsTest {

  @Test
  void failsWhenAnyExist(@TempDir Path tempDir) throws Exception {
    final Path file1 = Files.createFile(tempDir.resolve("file1"));
    final Path file2 = tempDir.resolve("file2");

    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileNotExists())
          .execute(
              file1.toString(),
              file2.toString()
          );

      assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
    });

    assertThatLines(errOut)
        .contains(file1 +" exists");
  }

  @Test
  void passesWhenAllMissing(@TempDir Path tempDir) throws Exception {
    final Path pathA = tempDir.resolve("fileA");
    final Path fileB = tempDir.resolve("fileB");

    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileNotExists())
          .execute(
              pathA.toString(),
              fileB.toString()
          );

      assertThat(exitCode).isEqualTo(ExitCode.OK);
    });

    assertThat(errOut).isBlank();
  }

  @Test
  void failsWhenGlobFindsAnyFiles(@TempDir Path tempDir) throws Exception {
    final Path file1 = Files.createFile(tempDir.resolve("file1"));

    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileNotExists())
          .execute(
              String.format("%s/file*", tempDir)
          );

      assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
    });

    assertThatLines(errOut)
        .hasSize(1)
        .element(0)
        .asString()
        .contains(file1.toString());
  }

  @Test
  void passesWhenGlobFindsNothing(@TempDir Path tempDir) throws Exception {
    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileNotExists())
          .execute(
              // working directory is top of project
              tempDir+"/*.md"
          );

      assertThat(exitCode).isEqualTo(0);
    });

    assertThat(errOut).isBlank();
  }
}
