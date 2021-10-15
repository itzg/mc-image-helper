package me.itzg.helpers.get;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import picocli.CommandLine;

@ExtendWith(MockServerExtension.class)
class GetCommandTest {
  private final ClientAndServer client;

  GetCommandTest(ClientAndServer client) {
    this.client = client;
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  @Test
  void outputsDownload() throws MalformedURLException {
    expectRequest("GET","/outputsDownload.txt",
        response()
            .withBody("Response content", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                buildMockedUrl("/outputsDownload.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Response content");
  }

  @Test
  void handlesExtraSlashAtStartOfPath() throws MalformedURLException {
    expectRequest("GET","/handlesExtraSlashAtStartOfPath.txt",
        response()
            .withBody("Response content", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                buildMockedUrl("//handlesExtraSlashAtStartOfPath.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Response content");
  }

  @Test
  void handlesNotFound() throws MalformedURLException {
    expectRequest("GET","/handlesNotFound",
        response()
            .withStatusCode(404)
            .withBody("<html><body>Not found</body></html>", MediaType.TEXT_HTML_UTF_8)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                buildMockedUrl("/handlesNotFound").toString()
            );

    assertThat(status).isEqualTo(1);
    assertThat(output.toString()).isEqualTo("");

  }

  @Nested
  class OutputToDir {

    @Test
    void saveFileFromGithubRelease(@TempDir Path tempDir) throws IOException {
      // 302 to CDN location
      // 200 with content-disposition: attachment; filename=mc-image-helper-1.4.0.zip
      expectRequest("GET", "/github/releases/file.txt", response()
          .withStatusCode(302)
          .withHeader("Location", buildMockedUrl("/cdn/1-2-3-4").toString()));
      expectRequest("GET", "/cdn/1-2-3-4", response()
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
                  buildMockedUrl("/github/releases/file.txt").toString()
              );

      final Path expectedFile = tempDir.resolve("final-name.txt");

      assertThat(status).isEqualTo(0);
      assertThat(expectedFile).exists();
      assertThat(expectedFile).hasContent("final content");
      assertThat(dontPruneThis).exists();
    }

    @Test
    void saveFileLikeBukkit(@TempDir Path tempDir) throws MalformedURLException {
      expectRequest("GET", "/bukkit/123/download", response()
          .withStatusCode(302)
          .withHeader("location", buildMockedUrl("/forgecdn/saveFileLikeBukkit.txt").toString()));
      expectRequest("GET", "/forgecdn/saveFileLikeBukkit.txt", response()
          .withBody("final content", MediaType.TEXT_PLAIN));

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  tempDir.toString(),
                  buildMockedUrl("/bukkit/123/download").toString()
              );

      final Path expectedFile = tempDir.resolve("saveFileLikeBukkit.txt");

      assertThat(status).isEqualTo(0);
      assertThat(expectedFile).exists();
      assertThat(expectedFile).hasContent("final content");

    }

    @Test
    void multipleUrisSeparated(@TempDir Path tempDir) throws MalformedURLException {
      expectRequest("GET", "/one", response()
          .withStatusCode(302)
          .withHeader("location", buildMockedUrl("/one.txt").toString()));
      expectRequest("GET", "/one.txt", response()
          .withBody("content for one", MediaType.TEXT_PLAIN));
      expectRequest("GET", "/two.txt", response()
          .withBody("content for two", MediaType.TEXT_PLAIN));

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  tempDir.toString(),
                  buildMockedUrl("/one").toString(),
                  buildMockedUrl("/two.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
      assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
    }

    @Test
    void combinesWithUrisFile(@TempDir Path tempDir) throws IOException {
      expectRequest("GET", "/one.txt", response()
          .withBody("content for one", MediaType.TEXT_PLAIN));
      expectRequest("GET", "/two.txt", response()
          .withBody("content for two", MediaType.TEXT_PLAIN));
      expectRequest("GET", "/%5B1.10.2%5Dthree.txt", response()
          .withBody("content for three", MediaType.TEXT_PLAIN));

      final ArrayList<String> lines = new ArrayList<>();
      lines.add(buildMockedUrl("/one.txt").toString());
      lines.add("");
      lines.add("#"+ buildMockedUrl("/notThis.txt"));
      lines.add(buildMockedUrl("/[1.10.2]three.txt").toString());
      final Path urisFile = Files.write(tempDir.resolve("uris.txt"), lines);

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o", tempDir.toString(),
                  "--uris-file", urisFile.toString(),
                  buildMockedUrl("/two.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
      assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
      assertThat(tempDir.resolve("[1.10.2]three.txt")).hasContent("content for three");
    }

    @Test
    void prunesOthers(@TempDir Path tempDir) throws IOException {
      expectRequest("GET", "/one.txt", response()
          .withBody("content for one", MediaType.TEXT_PLAIN));
      expectRequest("GET", "/two.txt", response()
          .withBody("content for two", MediaType.TEXT_PLAIN));

      final Path keepTxt = Files.createFile(tempDir.resolve("keep.txt"));
      final Path pruneJar = Files.createFile(tempDir.resolve("prune.jar"));
      final Path keepJar = Files.createFile(Files.createDirectory(tempDir.resolve("inner"))
          .resolve("keep.jar"));

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  tempDir.toString(),
                  "--prune-others", "*.jar",
                  // use default prune depth of 1
                  buildMockedUrl("/one.txt").toString(),
                  buildMockedUrl("/two.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
      assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
      assertThat(keepTxt).exists();
      assertThat(keepJar).exists();
      assertThat(pruneJar).doesNotExist();
    }

    @Test
    void pruneDepthIsUsed(@TempDir Path tempDir) throws IOException {
      expectRequest("GET", "/one.txt", response()
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
                  buildMockedUrl("/one.txt").toString()
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
      expectRequest("GET", "/one.txt", response()
          .withBody("content for one", MediaType.TEXT_PLAIN));
      expectRequest("GET", "/two.txt", response()
          .withBody("content for two", MediaType.TEXT_PLAIN));

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  tempDir.toString(),
                  String.join(",",
                      buildMockedUrl("/one.txt").toString(),
                      buildMockedUrl("/two.txt").toString()
                  )
              );

      assertThat(status).isEqualTo(0);
      assertThat(tempDir.resolve("one.txt")).hasContent("content for one");
      assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
    }

    @Test
    void skipExisting(@TempDir Path tempDir) throws IOException {
      expectRequest("HEAD", "/one.txt", response()
          .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
      expectRequest("GET", "/one.txt", response()
          .withBody("new content for one", MediaType.TEXT_PLAIN));
      expectRequest("HEAD", "/two.txt", response()
          .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
      expectRequest("GET", "/two.txt", response()
          .withBody("content for two", MediaType.TEXT_PLAIN));

      try (BufferedWriter writer = Files.newBufferedWriter(tempDir.resolve("one.txt"))) {
        writer.write("old content for one");
      }

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  tempDir.toString(),
                  "--skip-existing",
                  buildMockedUrl("/one.txt").toString(),
                  buildMockedUrl("/two.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(tempDir.resolve("one.txt")).hasContent("old content for one");
      assertThat(tempDir.resolve("two.txt")).hasContent("content for two");
    }

    @Test
    void skipExistingWithContentDisposition(@TempDir Path tempDir) throws MalformedURLException {
      // 200 with content-disposition: attachment; filename=mc-image-helper-1.4.0.zip
      expectRequest("GET","/cdn/1-2-3-4",
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
                  buildMockedUrl("/cdn/1-2-3-4").toString()
              );

      final Path expectedFile = tempDir.resolve("final-name.txt");

      assertThat(status).isEqualTo(0);
      assertThat(expectedFile).exists();
      assertThat(expectedFile).hasContent("final content");
    }

    @Test
    void doesntWriteFileWhenNotFound(@TempDir Path tempDir) throws MalformedURLException {
      expectRequest("GET", "/doesntWriteFileWhenNotFound.txt", response()
          .withStatusCode(404)
          .withBody("<html><body>Not found</body></html>", MediaType.TEXT_HTML_UTF_8));

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "-o", tempDir.toString(),
                  buildMockedUrl("/doesntWriteFileWhenNotFound.txt").toString()
              );

      assertThat(status).isEqualTo(1);
      assertThat(tempDir).isEmptyDirectory();
    }

  }

  @Nested
  class OutputToFile {
    @Test
    void successful(@TempDir Path tempDir) throws MalformedURLException {
      expectRequest("GET","/downloadsToFile.txt",
          response()
              .withBody("Response content to file", MediaType.TEXT_PLAIN)
      );

      final Path expectedFile = tempDir.resolve("out.txt");

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  expectedFile.toString(),
                  buildMockedUrl("/downloadsToFile.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(expectedFile).exists();
      assertThat(expectedFile).hasContent("Response content to file");
    }

    @Test
    void doesNotAllowMultipleUris(@TempDir Path tempDir) throws MalformedURLException {
      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  tempDir.resolve("notused.txt").toString(),
                  buildMockedUrl("/one.txt").toString(),
                  buildMockedUrl("/two.txt").toString()
              );

      assertThat(status).isEqualTo(2);
    }

    @Test
    void skipExisting(@TempDir Path tempDir) throws IOException {
      expectRequest("GET", "/one.txt",
          response()
              .withBody("new content for one", MediaType.TEXT_PLAIN)
      );

      final Path fileToSkip = tempDir.resolve("one.txt");
      try (BufferedWriter writer = Files.newBufferedWriter(fileToSkip)) {
        writer.write("old content for one");
      }

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "-o",
                  fileToSkip.toString(),
                  "--skip-existing",
                  buildMockedUrl("/one.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(tempDir.resolve("one.txt")).hasContent("old content for one");
    }
  }

  private URL buildMockedUrl(String s) throws MalformedURLException {
    return new URL("http", "localhost", client.getLocalPort(), s);
  }


  @Nested
  class JsonPath {

    @Test
    void stringField() throws MalformedURLException {
      expectRequest("GET", "/string.json",
          response()
              .withBody("{\"field\": \"a string\"}", MediaType.APPLICATION_JSON)
      );

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", "$.field",
                  buildMockedUrl("/string.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo("a string"+ lineSeparator());
    }

    @Test
    void numberField() throws MalformedURLException {
      expectRequest("GET", "/number.json",
          response()
              .withBody("{\"field\": 543}", MediaType.APPLICATION_JSON)
      );

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", "$.field",
                  buildMockedUrl("/number.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo("543"+ lineSeparator());
    }

    @Test
    void booleanField() throws MalformedURLException {
      expectRequest("GET", "/boolean.json",
          response()
              .withBody("{\"field\": true}", MediaType.APPLICATION_JSON)
      );

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", "$.field",
                  buildMockedUrl("/boolean.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo("true"+ lineSeparator());
    }

    @Test
    void handlesMissingField() throws MalformedURLException {
      expectRequest("GET", "/content.json",
          response()
              .withBody("{}", MediaType.APPLICATION_JSON)
      );

      final StringWriter stdout = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(stdout))
              .execute(
                  "--json-path", "$.field",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(1);
    }

    @Test
    void missingRootOutputsNull() throws MalformedURLException {
      expectRequest("GET", "/content.json",
          response()
              .withBody("{\"field\":\"value\"}", MediaType.APPLICATION_JSON)
      );

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", ".field",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo("null"+ lineSeparator());
    }

    @Test
    void useConcatWithListField() throws MalformedURLException {
      expectRequest("GET", "/content.json", response()
          .withBody("{\"field\":[\"one\",\"two\"]}", MediaType.APPLICATION_JSON));

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", "$.field.concat()",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo("onetwo" + lineSeparator());
    }

  }

  private void expectRequest(String method, String path, HttpResponse httpResponse) {
    client
        .when(
            request()
                .withMethod(method)
                .withPath(path)
        )
        .respond(
            httpResponse
        );
  }

}