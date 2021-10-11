package me.itzg.helpers.get;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
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
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/outputsDownload.txt")
        )
        .respond(
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
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/handlesExtraSlashAtStartOfPath.txt")
        )
        .respond(
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
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/handlesNotFound")
        )
        .respond(
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

  @Test
  void doesntWriteFileWhenNotFound(@TempDir Path tempDir) throws MalformedURLException {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/doesntWriteFileWhenNotFound.txt")
        )
        .respond(
            response()
                .withStatusCode(404)
                .withBody("<html><body>Not found</body></html>", MediaType.TEXT_HTML_UTF_8)
        );

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

  @Test
  void downloadsToFile(@TempDir Path tempDir) throws MalformedURLException {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/downloadsToFile.txt")
        )
        .respond(
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

  private URL buildMockedUrl(String s) throws MalformedURLException {
    return new URL("http", "localhost", client.getLocalPort(), s);
  }

  @Test
  void saveFileFromGithubRelease(@TempDir Path tempDir) throws MalformedURLException {
    // 302 to CDN location
    // 200 with content-disposition: attachment; filename=mc-image-helper-1.4.0.zip
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/github/releases/file.txt")
        )
        .respond(
            response()
                .withStatusCode(302)
                .withHeader("Location", buildMockedUrl("/cdn/1-2-3-4").toString())
        );
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/cdn/1-2-3-4")
        )
        .respond(
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
                buildMockedUrl("/github/releases/file.txt").toString()
            );

    final Path expectedFile = tempDir.resolve("final-name.txt");

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("final content");
  }

  @Test
  void saveFileLikeBukkit(@TempDir Path tempDir) throws MalformedURLException {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/bukkit/123/download")
        )
        .respond(
            response()
                .withStatusCode(302)
                .withHeader("location", buildMockedUrl("/forgecdn/saveFileLikeBukkit.txt").toString())
        );
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/forgecdn/saveFileLikeBukkit.txt")
        )
        .respond(
            response()
                .withBody("final content", MediaType.TEXT_PLAIN)
        );

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

  @Nested
  class JsonPath {

    @Test
    void stringField() throws MalformedURLException {
      client
          .when(
              request()
                  .withMethod("GET")
                  .withPath("/string.json")
          )
          .respond(
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
      client
          .when(
              request()
                  .withMethod("GET")
                  .withPath("/number.json")
          )
          .respond(
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
      client
          .when(
              request()
                  .withMethod("GET")
                  .withPath("/boolean.json")
          )
          .respond(
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
      client
          .when(
              request()
                  .withMethod("GET")
                  .withPath("/content.json")
          )
          .respond(
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
      client
          .when(
              request()
                  .withMethod("GET")
                  .withPath("/content.json")
          )
          .respond(
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
      client
          .when(
              request()
                  .withMethod("GET")
                  .withPath("/content.json")
          )
          .respond(
              response()
                  .withBody("{\"field\":[\"one\",\"two\"]}", MediaType.APPLICATION_JSON)
          );

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", "$.field.concat()",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo("onetwo"+ lineSeparator());
    }

  }
}