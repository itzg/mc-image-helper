package me.itzg.helpers.get;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpResponse.response;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.MediaType;
import picocli.CommandLine;

@ExtendWith(MockServerExtension.class)
class OutputToFileTest {
  private final ClientAndServer client;
  private final MockServerSupport mock;

  OutputToFileTest(ClientAndServer client) {
    this.client = client;
    mock = new MockServerSupport(client);
  }

  @AfterEach
  void tearDown() {
    client.reset();
    TestLoggingAppender.reset();
  }

  @Test
  void successful(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/downloadsToFile.txt",
        response()
            .withBody("Response content to file", MediaType.TEXT_PLAIN)
    );

    final Path expectedFile = tempDir.resolve("out.txt");

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                mock.buildMockedUrl("/downloadsToFile.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("Response content to file");
  }

  @Test
  void failsAcceptMismatch(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/downloadsToFile.txt",
        request -> request.withHeader("Accept", "application/java-archive", "application/zip"),
        response()
            .withBody("Response content to file", MediaType.TEXT_PLAIN)
    );

    final Path expectedFile = tempDir.resolve("out.txt");

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                "--accept", "application/java-archive",
                "--accept", "application/zip",
                mock.buildMockedUrl("/downloadsToFile.txt").toString()
            );

    assertThat(status).isEqualTo(1);
    assertThat(expectedFile).doesNotExist();
  }

  @Test
  void succeedsAcceptMatch(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/downloadsToFile.zip",
        request -> request.withHeader("Accept", "application/java-archive", "application/zip"),
        response()
            .withBody("Fake zip content", MediaType.create("application", "zip"))
    );

    final Path expectedFile = tempDir.resolve("out.zip");

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                "--accept", "application/java-archive",
                "--accept", "application/zip",
                mock.buildMockedUrl("/downloadsToFile.zip").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
  }

  @Test
  void succeedsAcceptMatch_commaSeparated(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/downloadsToFile.zip",
        request -> request.withHeader("Accept", "application/java-archive", "application/zip"),
        response()
            .withBody("Fake zip content", MediaType.create("application", "zip"))
    );

    final Path expectedFile = tempDir.resolve("out.zip");

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                "--accept", "application/java-archive,application/zip",
                mock.buildMockedUrl("/downloadsToFile.zip").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
  }

  @Test
  void doesNotAllowMultipleUris(@TempDir Path tempDir) throws MalformedURLException {
    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.resolve("notused.txt").toString(),
                mock.buildMockedUrl("/one.txt").toString(),
                mock.buildMockedUrl("/two.txt").toString()
            );

    assertThat(status).isEqualTo(2);
  }

  @Test
  void skipExisting(@TempDir Path tempDir) throws IOException {
    mock.expectRequest("GET", "/one.txt",
        response()
            .withBody("new content for one", MediaType.TEXT_PLAIN)
    );

    final Path fileToSkip = tempDir.resolve("one.txt");
    try (BufferedWriter writer = Files.newBufferedWriter(fileToSkip)) {
      writer.write("old content for one");
    }

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "-o",
                fileToSkip.toString(),
                "--skip-existing",
                "--output-filename",
                mock.buildMockedUrl("/one.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(tempDir.resolve("one.txt")).hasContent("old content for one");
    assertThat(output.toString()).isEqualTo(fileToSkip + lineSeparator());
  }

  @Test
  void skipsUpToDate(@TempDir Path tempDir) throws IOException {
    final Path fileToSkip = Files.createFile(tempDir.resolve("existing.txt"));
    // set it to a known time "in the past"
    Files.setLastModifiedTime(fileToSkip, FileTime.from(1637551412, TimeUnit.SECONDS));

    mock.expectRequest("GET", "/existing.txt", request ->
            request.withHeader("if-modified-since", "Mon, 22 Nov 2021 03:23:32 GMT"),
        response()
            .withStatusCode(304)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "-o",
                fileToSkip.toString(),
                "--skip-up-to-date",
                "--output-filename",
                mock.buildMockedUrl("/existing.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(Files.getLastModifiedTime(fileToSkip).to(TimeUnit.SECONDS)).isEqualTo(1637551412);
  }


  @Test
  void skipsUpToDate_butDownloadsWhenAbsent(@TempDir Path tempDir) throws IOException {
    final Path fileToDownload = tempDir.resolve("new.txt");

    mock.expectRequest("GET", "/new.txt",
        response()
            .withBody("New content", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "-o",
                fileToDownload.toString(),
                "--skip-up-to-date",
                "--output-filename",
                mock.buildMockedUrl("/new.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(fileToDownload).exists();
    assertThat(fileToDownload).hasContent("New content");
  }

  @Test
  void successfulWithTemporaryFile(@TempDir Path tempDir) throws MalformedURLException, IOException {
    mock.expectRequest("GET", "/downloadsToFile.txt",
        response()
            .withBody("Response content to file", MediaType.TEXT_PLAIN)
    );

    final Path expectedFile = tempDir.resolve("out.txt");

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                "--use-temp-file",
                mock.buildMockedUrl("/downloadsToFile.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("Response content to file");
    // The temporary file with .download extension should no longer exist after successful download
    assertThat(tempDir.resolve("out.txt.download")).doesNotExist();
  }
  
  @Test
  void handlesExistingDownloadFile(@TempDir Path tempDir) throws MalformedURLException, IOException {
    mock.expectRequest("GET", "/downloadsToFile.txt",
        response()
            .withBody("New content", MediaType.TEXT_PLAIN)
    );

    final Path expectedFile = tempDir.resolve("out.txt");
    final Path downloadFile = tempDir.resolve("out.txt.download");
    
    // Create a pre-existing .download file with different content
    Files.writeString(downloadFile, "Partial old content");
    
    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                "--use-temp-file",
                mock.buildMockedUrl("/downloadsToFile.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("New content");
    // The temporary file should be gone
    assertThat(downloadFile).doesNotExist();
  }
  
  @Test
  void preservesOriginalWhenErrorOccurs(@TempDir Path tempDir) throws MalformedURLException, IOException {
    mock.expectRequest("GET", "/errorFile.txt",
        response()
            .withStatusCode(500)
            .withBody("Server error", MediaType.TEXT_PLAIN)
    );

    final Path expectedFile = tempDir.resolve("out.txt");
    final String originalContent = "Original content that should be preserved";
    
    // Create the original file with content that should remain untouched
    Files.writeString(expectedFile, originalContent);
    
    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                "--use-temp-file",
                mock.buildMockedUrl("/errorFile.txt").toString()
            );

    // Should fail with non-zero status
    assertThat(status).isNotEqualTo(0);
    // Original file should still exist with unchanged content
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent(originalContent);
    // Any temporary download file should be cleaned up
    assertThat(tempDir.resolve("out.txt.download")).doesNotExist();
  }
  
  @Test
  void preservesOriginalWhenDownloadHasInvalidContent(@TempDir Path tempDir) throws MalformedURLException, IOException {
    // Set up a request that will result in an error during processing
    // We'll return content with a valid Content-Length but corrupted/truncated data
    mock.expectRequest("GET", "/interruptedFile.txt",
        response()
            .withHeader("Content-Length", "1000")  // Much larger than actual content
            .withBody("This is only part of the expected content...")  // Truncated content
    );

    final Path expectedFile = tempDir.resolve("out.txt");
    final String originalContent = "Original content that should remain intact";
    
    // Create the original file with content that should remain untouched
    Files.writeString(expectedFile, originalContent);
    
    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                expectedFile.toString(),
                "--use-temp-file",
                mock.buildMockedUrl("/interruptedFile.txt").toString()
            );
    
    // Original file should still exist with unchanged content
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent(originalContent);
    
    // Any temporary download file should be cleaned up
    assertThat(tempDir.resolve("out.txt.download")).doesNotExist();
  }

}
