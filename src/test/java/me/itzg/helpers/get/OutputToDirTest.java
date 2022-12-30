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
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import picocli.CommandLine;

@ExtendWith(MockServerExtension.class)
class OutputToDirTest {
  private final ClientAndServer client;
  private final MockServerSupport mock;

  OutputToDirTest(ClientAndServer client) {
    this.client = client;
    mock = new MockServerSupport(client);
  }

  @AfterEach
  void tearDown() {
    client.reset();
    TestLoggingAppender.reset();
  }

  @Test
  void saveFileFromGithubRelease(@TempDir Path tempDir) throws IOException {
    // 302 to CDN location
    // 200 with content-disposition: attachment; filename=mc-image-helper-1.4.0.zip
    mock.expectRequest("GET", "/github/releases/file.txt", response()
        .withStatusCode(302)
        .withHeader("Location", mock.buildMockedUrl("/cdn/1-2-3-4").toString()));
    mock.expectRequest("GET", "/cdn/1-2-3-4", response()
        .withStatusCode(200)
        .withHeader("content-disposition", "attachment; filename=final-name.txt")
        .withBody("final content", MediaType.TEXT_PLAIN));

    final Path dontPruneThis = tempDir.resolve("keep.jar");
    Files.createFile(dontPruneThis);

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.toString(),
                mock.buildMockedUrl("/github/releases/file.txt").toString()
            );

    final Path expectedFile = tempDir.resolve("final-name.txt");

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("final content");
    assertThat(dontPruneThis).exists();
  }

  @Test
  void saveFileLikeBukkit(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/bukkit/123/download", response()
        .withStatusCode(302)
        .withHeader("location",
            mock.buildMockedUrl("/forgecdn/saveFileLikeBukkit.txt").toString()));
    mock.expectRequest("GET", "/forgecdn/saveFileLikeBukkit.txt", response()
        .withBody("final content", MediaType.TEXT_PLAIN));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.toString(),
                mock.buildMockedUrl("/bukkit/123/download").toString()
            );

    final Path expectedFile = tempDir.resolve("saveFileLikeBukkit.txt");

    assertThat(status).isEqualTo(0);
    assertThat(tempDir.resolve("download")).doesNotExist();
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("final content");

  }

  @Test
  void multipleUrisSeparated(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/one", response()
        .withStatusCode(302)
        .withHeader("location", mock.buildMockedUrl("/one.txt").toString()));
    mock.expectRequest("GET", "/one.txt", response()
        .withBody("content for one", MediaType.TEXT_PLAIN));
    mock.expectRequest("GET", "/two.txt", response()
        .withBody("content for two", MediaType.TEXT_PLAIN));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.toString(),
                mock.buildMockedUrl("/one").toString(),
                mock.buildMockedUrl("/two.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
    assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
  }

  @Test
  void combinesWithUrisFile(@TempDir Path tempDir) throws IOException {
    mock.expectRequest("GET", "/one.txt", response()
        .withBody("content for one", MediaType.TEXT_PLAIN));
    mock.expectRequest("GET", "/two.txt", response()
        .withBody("content for two", MediaType.TEXT_PLAIN));
    mock.expectRequest("GET", "/%5B1.10.2%5Dthree.txt", response()
        .withBody("content for three", MediaType.TEXT_PLAIN));

    final ArrayList<String> lines = new ArrayList<>();
    lines.add(mock.buildMockedUrl("/one.txt").toString());
    lines.add("");
    lines.add("#" + mock.buildMockedUrl("/notThis.txt"));
    lines.add(mock.buildMockedUrl("/[1.10.2]three.txt").toString());
    final Path urisFile = Files.write(tempDir.resolve("uris.txt"), lines);

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o", tempDir.toString(),
                "--uris-file", urisFile.toString(),
                mock.buildMockedUrl("/two.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
    assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
    assertThat(tempDir.resolve("[1.10.2]three.txt")).hasContent("content for three");
  }

  @Test
  void prunesOthers(@TempDir Path tempDir) throws IOException {
    mock.expectRequest("HEAD", "/one.txt", response()
        .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
    mock.expectRequest("HEAD", "/two.txt", response()
        .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
    mock.expectRequest("GET", "/two.txt", response()
        .withBody("content for two", MediaType.TEXT_PLAIN));

    final Path keep = Files.createFile(tempDir.resolve("keep.dat"));
    final Path pruneJar = Files.createFile(tempDir.resolve("prune.jar"));
    final Path keepJar = Files.createFile(Files.createDirectory(tempDir.resolve("inner"))
        .resolve("keep.jar"));

    // this one will be skipped
    final Path oneTxt = Files.createFile(tempDir.resolve("one.txt"));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.toString(),
                "--skip-existing",
                "--prune-others", "*.txt,*.jar",
                // use default prune depth of 1
                mock.buildMockedUrl("/one.txt").toString(),
                mock.buildMockedUrl("/two.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(oneTxt).exists();
    assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
    assertThat(keep).exists();
    assertThat(keepJar).exists();
    assertThat(pruneJar).doesNotExist();
  }

  @Test
  void pruneDepthIsUsed(@TempDir Path tempDir) throws IOException {
    mock.expectRequest("GET", "/one.txt", response()
        .withBody("content for one", MediaType.TEXT_PLAIN));

    final Path keepTxt = Files.createFile(tempDir.resolve("keep.txt"));
    final Path pruneTopJar = Files.createFile(tempDir.resolve("pruneTop.jar"));
    final Path outerDir = Files.createDirectory(tempDir.resolve("outer"));
    final Path pruneOuterJar = Files.createFile(outerDir.resolve("pruneOuter.jar"));
    final Path keepJar = Files.createFile(Files.createDirectory(outerDir.resolve("inner"))
        .resolve("keep.jar"));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.toString(),
                "--prune-others", "*.jar",
                "--prune-depth", "2",
                mock.buildMockedUrl("/one.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
    assertThat(keepTxt).exists();
    assertThat(pruneOuterJar).doesNotExist();
    assertThat(pruneTopJar).doesNotExist();
    assertThat(keepJar).exists();
  }

  @Test
  void multipleUrisConcatenated(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/one.txt", response()
        .withBody("content for one", MediaType.TEXT_PLAIN));
    mock.expectRequest("GET", "/two.txt", response()
        .withBody("content for two", MediaType.TEXT_PLAIN));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.toString(),
                String.join(",",
                    mock.buildMockedUrl("/one.txt").toString(),
                    mock.buildMockedUrl("/two.txt").toString()
                )
            );

    assertThat(status).isEqualTo(0);
    assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
    assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
  }

  @Test
  void skipExisting(@TempDir Path tempDir) throws IOException {
    mock.expectRequest("HEAD", "/one.txt", response()
        .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
    mock.expectRequest("GET", "/one.txt", response()
        .withBody("new content for one", MediaType.TEXT_PLAIN));
    mock.expectRequest("HEAD", "/two.txt", response()
        .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
    mock.expectRequest("GET", "/two.txt", response()
        .withBody("content for two", MediaType.TEXT_PLAIN));

    final Path fileOne = tempDir.resolve("one.txt");
    final Path fileTwo = tempDir.resolve("two.txt");
    try (BufferedWriter writer = Files.newBufferedWriter(fileOne)) {
      writer.write("old content for one");
    }

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "-o",
                tempDir.toString(),
                "--skip-existing",
                "--output-filename",
                mock.buildMockedUrl("/one.txt").toString(),
                mock.buildMockedUrl("/two.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(fileOne).hasContent("old content for one");
    assertThat(fileTwo).hasContent("content for two");

    final String[] parts = output.toString().split(lineSeparator());
    assertThat(parts).containsExactlyInAnyOrder(
        fileOne.toString(), fileTwo.toString()
    );
  }

  @Test
  void skipExistingWithContentDisposition(@TempDir Path tempDir) throws MalformedURLException {
    // 200 with content-disposition: attachment; filename=mc-image-helper-1.4.0.zip
    mock.expectRequest("GET", "/cdn/1-2-3-4",
        response()
            .withStatusCode(200)
            .withHeader("content-disposition", "attachment; filename=final-name.txt")
            .withBody("final content", MediaType.TEXT_PLAIN)
    );

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "-o",
                tempDir.toString(),
                mock.buildMockedUrl("/cdn/1-2-3-4").toString()
            );

    final Path expectedFile = tempDir.resolve("final-name.txt");

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("final content");
  }

  @Test
  void skipsUpToDate(@TempDir Path tempDir) throws IOException {
    final Path fileToSkip = Files.createFile(tempDir.resolve("existing.txt"));
    // set it to a known time "in the past"
    Files.setLastModifiedTime(fileToSkip, FileTime.from(1637551412, TimeUnit.SECONDS));

    mock.expectRequest("HEAD", "/existing.txt", response()
        .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
    mock.expectRequest("GET", "/existing.txt",
        request -> request.withHeader("if-modified-since", "Mon, 22 Nov 2021 03:23:32 GMT"),
        response()
            .withStatusCode(304)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "-o",
                tempDir.toString(),
                "--skip-up-to-date",
                "--output-filename",
                mock.buildMockedUrl("/existing.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(Files.getLastModifiedTime(fileToSkip).to(TimeUnit.SECONDS)).isEqualTo(1637551412);
  }

  @Test
  void doesntWriteFileWhenNotFound(@TempDir Path tempDir) throws MalformedURLException {
    mock.expectRequest("GET", "/doesntWriteFileWhenNotFound.txt", response()
        .withStatusCode(404)
        .withBody("<html><body>Not found</body></html>", MediaType.TEXT_HTML_UTF_8));

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "-o", tempDir.toString(),
                mock.buildMockedUrl("/doesntWriteFileWhenNotFound.txt").toString()
            );

    assertThat(status).isEqualTo(1);
    assertThat(tempDir).isEmptyDirectory();
  }

}
