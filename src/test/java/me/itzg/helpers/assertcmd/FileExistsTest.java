package me.itzg.helpers.assertcmd;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class FileExistsTest {

  @Test
  void passesWhenAllExist(@TempDir Path tempDir) throws Exception {
    final Path file1 = Files.createFile(tempDir.resolve("file1"));
    final Path file2 = Files.createFile(tempDir.resolve("file2"));

    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileExists())
          .execute(
              file1.toString(),
              file2.toString()
          );

      assertThat(exitCode).isEqualTo(0);
    });

    assertThat(errOut).isBlank();
  }

  @Test
  void failsWhenSomeMissing(@TempDir Path tempDir) throws Exception {
    final Path file1 = Files.createFile(tempDir.resolve("file1"));
    final Path file2 = Files.createFile(tempDir.resolve("file2"));
    final Path pathA = tempDir.resolve("fileA");
    final Path fileB = tempDir.resolve("fileB");

    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileExists())
          .execute(
              file1.toString(),
              pathA.toString(),
              file2.toString(),
              fileB.toString()
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).contains(
        "fileA does not exist",
        "fileB does not exist"
    );
  }

  @Test
  void passesWhenGlobFindsAllFiles(@TempDir Path tempDir) throws Exception {
    Files.createFile(tempDir.resolve("file1"));
    Files.createFile(tempDir.resolve("file2"));

    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileExists())
          .execute(
              String.format("%s/file*", tempDir)
          );

      assertThat(exitCode).isEqualTo(0);
    });

    assertThat(errOut).isBlank();
  }

  @Test
  void passesWhenGlobFileInWorkingDirectory() throws Exception {
    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileExists())
          .execute(
              // working directory is top of project
              "*.md"
          );

      assertThat(exitCode).isEqualTo(0);
    });

    assertThat(errOut).isBlank();
  }

  @Test
  void passesWhenGlobFileSubdir() throws Exception {
    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileExists())
          .execute(
              // working directory is top of project
              "gradle/wrapper/*.jar"
          );

      assertThat(exitCode).isEqualTo(0);
    });

    assertThat(errOut).isBlank();
  }

  @Test
  void passesWhenGlobDoubleStarAndMultipleMatches() throws Exception {
    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileExists())
          .execute(
              // working directory is top of project
              "**/gradle-wrapper.*"
          );

      assertThat(exitCode).isEqualTo(0);
    });

    assertThat(errOut).isBlank();
  }

  @Test
  void failsWhenGlobFailsToFindFiles(@TempDir Path tempDir) throws Exception {
    Files.createFile(tempDir.resolve("file1"));
    Files.createFile(tempDir.resolve("file2"));

    final String errOut = tapSystemErr(() -> {
      int exitCode = new CommandLine(new FileExists())
          .execute(
              String.format("%s/fileA*", tempDir)
          );

      assertThat(exitCode).isEqualTo(1);
    });

    assertThat(errOut).contains(
        "fileA* does not exist"
    );
  }
}
