package me.itzg.helpers.get;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@ExtendWith(MockServerExtension.class)
class GetCommandTest {
  private final ClientAndServer client;

  GetCommandTest(ClientAndServer client) {
    this.client = client;
  }

  @AfterEach
  void tearDown() {
    client.reset();
    TestLoggingAppender.reset();
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
            .withBody("<html><body>Not found</body></html>", MediaType.TEXT_HTML_UTF_8), 
        5
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
  void handlesRetryOn403ThenSuccess() throws MalformedURLException {
    expectRequest("GET","/handlesRetry",
        response()
            .withStatusCode(403)
            .withBody("Permission denied", MediaType.TEXT_PLAIN),
        1
    );
    expectRequest("GET","/handlesRetry",
        response()
            .withStatusCode(200)
            .withBody("Success", MediaType.TEXT_PLAIN),
        1
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "--retry-count=1",
                "--retry-delay=1",
                buildMockedUrl("/handlesRetry").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Success");

    client.verify(
        request("/handlesRetry"),
        VerificationTimes.exactly(2)
    );
  }

  @Test
  void handlesRetryThenFails() throws MalformedURLException {
    expectRequest("GET","/handlesRetry",
        response()
            .withStatusCode(403)
            .withBody("Permission denied", MediaType.TEXT_PLAIN),
        2
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "--retry-count=1",
                "--retry-delay=1",
                buildMockedUrl("/handlesRetry").toString()
            );

    assertThat(status).isEqualTo(1);

    client.verify(
        request("/handlesRetry"),
        VerificationTimes.exactly(2)
    );
  }

  @Test
  void usesGivenAcceptHeader() throws MalformedURLException {
    expectRequest("GET","/content.txt",
        request -> request.withHeader("accept", "text/plain"),
        response()
            .withBody("Some text", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "--accept", "text/plain",
                buildMockedUrl("/content.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Some text");
  }

  @Test
  void usesGivenApiKeyHeader() throws MalformedURLException {
    expectRequest("GET","/content.txt",
        request -> request.withHeader("x-api-key", "xxxxxx"),
        response()
            .withBody("Some text", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                "--apikey", "xxxxxx",
                buildMockedUrl("/content.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Some text");
  }

  @Nested
  class ExistsTests {

    @Test
    void okWhenExists() throws MalformedURLException {
      expectRequest("HEAD", "/exists",
          response("Here!"));

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "--exists",
                  buildMockedUrl("/exists").toString()
              );

      assertThat(status).isEqualTo(0);
    }

    @Test
    void notOkWhenOneMissing() throws MalformedURLException {
      expectRequest("HEAD", "/exists",
          response("Here!"));
      expectRequest("HEAD", "/notHere",
          response().withStatusCode(404));

      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "--exists",
                  buildMockedUrl("/exists").toString(),
                  buildMockedUrl("/notHere").toString()
              );

      assertThat(status).isEqualTo(ExitCode.SOFTWARE);
    }

    @Test
    void includesAcceptHeader() throws MalformedURLException {
      expectRequest("HEAD", "/exists",
          request -> request.withHeader("accept", "text/plain"),
          response("Here!"));


      final int status =
          new CommandLine(new GetCommand())
              .execute(
                  "--exists",
                  "--accept", "text/plain",
                  buildMockedUrl("/exists").toString()
              );

      assertThat(status).isEqualTo(0);

    }
  }

  @Nested
  class OutputToDirTests {

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
      expectRequest("HEAD", "/one.txt", response()
          .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
      expectRequest("HEAD", "/two.txt", response()
          .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
      expectRequest("GET", "/two.txt", response()
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
                  buildMockedUrl("/one.txt").toString(),
                  buildMockedUrl("/two.txt").toString()
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
                  buildMockedUrl("/one.txt").toString(),
                  buildMockedUrl("/two.txt").toString()
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
    void skipsUpToDate(@TempDir Path tempDir) throws IOException {
      final Path fileToSkip = Files.createFile(tempDir.resolve("existing.txt"));
      // set it to a known time "in the past"
      Files.setLastModifiedTime(fileToSkip, FileTime.from(1637551412, TimeUnit.SECONDS));

      expectRequest("HEAD", "/existing.txt", response()
          .withStatusCode(HttpStatusCode.NO_CONTENT_204.code()));
      expectRequest("GET", "/existing.txt",
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
                  buildMockedUrl("/existing.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(Files.getLastModifiedTime(fileToSkip).to(TimeUnit.SECONDS)).isEqualTo(1637551412);
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
  class OutputToFileTests {
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

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "-o",
                  fileToSkip.toString(),
                  "--skip-existing",
                  "--output-filename",
                  buildMockedUrl("/one.txt").toString()
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

      expectRequest("GET", "/existing.txt", request ->
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
                  buildMockedUrl("/existing.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(Files.getLastModifiedTime(fileToSkip).to(TimeUnit.SECONDS)).isEqualTo(1637551412);
    }


    @Test
    void skipsUpToDate_butDownloadsWhenAbsent(@TempDir Path tempDir) throws IOException {
      final Path fileToDownload = tempDir.resolve("new.txt");

      expectRequest("GET", "/new.txt",
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
                  buildMockedUrl("/new.txt").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(fileToDownload).exists();
      assertThat(fileToDownload).hasContent("New content");
    }

  }

  private URL buildMockedUrl(String s) throws MalformedURLException {
    return new URL("http", "localhost", client.getLocalPort(), s);
  }


  @Nested
  class JsonPathTests {

    @Test
    void stringField() throws MalformedURLException {
      expectRequest("GET", "/string.json",
          acceptJson(),
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

    private RequestCustomizer acceptJson() {
      return request -> request.withHeader("accept", "application/json");
    }

    @Test
    void handlesJqStylePath() throws MalformedURLException {
      expectRequest("GET", "/string.json",
          acceptJson(),
          response()
              .withBody("{\"field\": \"a string\"}", MediaType.APPLICATION_JSON)
      );

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", ".field",
                  buildMockedUrl("/string.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo("a string"+ lineSeparator());
    }

    @Test
    void numberField() throws MalformedURLException {
      expectRequest("GET", "/number.json",
          acceptJson(),
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
          acceptJson(),
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
    void handlesMissingField_defaultOutputNull() throws MalformedURLException {
      expectRequest("GET", "/content.json",
          acceptJson(),
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

      assertThat(status).isEqualTo(0);
      assertThat(stdout.toString()).isEqualTo("null"+lineSeparator());
      assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    @Test
    void handlesMissingIntermediateField() throws MalformedURLException {
      expectRequest("GET", "/content.json",
          acceptJson(),
          response()
              .withBody("{\n"
                  + "  \"downloads\": {\n"
                  + "    \"client\": {\n"
                  + "      \"url\": \"https://launcher.mojang.com/v1/objects/b679fea27f2284836202e9365e13a82552092e5d/client.jar\"\n"
                  + "    }\n"
                  + "  }\n"
                  + "}", MediaType.APPLICATION_JSON)
      );

      final StringWriter stdout = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(stdout))
              .execute(
                  "--json-path", "$.downloads.server.url",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(stdout.toString()).isEqualTo("null"+lineSeparator());
      assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    @Test
    void handlesMissingField_alternateValue() throws MalformedURLException {
      expectRequest("GET", "/content.json",
          acceptJson(),
          response()
              .withBody("{}", MediaType.APPLICATION_JSON)
      );

      final StringWriter stdout = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(stdout))
              .execute(
                  "--json-path", "$.field",
                  "--json-value-when-missing", "missing",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(stdout.toString()).isEqualTo("missing"+lineSeparator());
      assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    /**
     * This test case captures the case of using forge's promotions_slim.json
     * where a "{version}-recommended" entry is not present.
     */
    @Test
    void handlesMissingField_errorWhenEmptyValue() throws MalformedURLException {
      expectRequest("GET", "/content.json",
          acceptJson(),
          response()
              .withBody("{\"promos\": {\"1.17.1-latest\": \"37.0.95\"}}", MediaType.APPLICATION_JSON)
      );

      final StringWriter stdout = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(stdout))
              .execute(
                  "--json-path", "$.promos['1.17.1-recommended']",
                  "--json-value-when-missing", "",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(1);
      assertThat(stdout.toString()).isEqualTo("");
      assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    @Test
    void useConcatWithListField() throws MalformedURLException {
      expectRequest("GET", "/content.json",
          acceptJson(),
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
      assertThat(output.toString()).isEqualTo("onetwo" + lineSeparator());
    }


    @ParameterizedTest
    @MethodSource("me.itzg.helpers.get.GetCommandTest#argsForJsonPathQuery")
    void supportsQueryOfStringValues(String content, String expected) throws MalformedURLException {
      expectRequest("GET", "/content.json",
          acceptJson(),
          response()
              .withBody(content, MediaType.APPLICATION_JSON)
      );

      final StringWriter output = new StringWriter();
      final int status =
          new CommandLine(new GetCommand())
              .setOut(new PrintWriter(output))
              .execute(
                  "--json-path", "$.values[?(@.name == 'one')].value",
                  buildMockedUrl("/content.json").toString()
              );

      assertThat(status).isEqualTo(0);
      assertThat(output.toString()).isEqualTo(expected + lineSeparator());

    }
  }

  @SuppressWarnings("unused")
  static Arguments[] argsForJsonPathQuery() {
    return new Arguments[]{
        arguments(
            "{\"values\": [{\"value\": \"v1\", \"name\": \"one\"}, {\"value\": \"v2\", \"name\": \"two\"}]}",
            "v1"),
        arguments(
            "{\"values\": [{\"value\": 1, \"name\": \"one\"}, {\"value\": 1, \"name\": \"two\"}]}",
            "1"),
        arguments(
            "{\"values\": [{\"value\": true, \"name\": \"one\"}, {\"value\": false, \"name\": \"two\"}]}",
            "true")
    };
  }

  @FunctionalInterface
  interface RequestCustomizer {
    HttpRequest customize(HttpRequest request);
  }

  private void expectRequest(String method, String path, HttpResponse httpResponse) {
    expectRequest(method, path, request -> request, httpResponse);
  }

  private void expectRequest(String method, String path, HttpResponse httpResponse, int responseTimes) {
    expectRequest(method, path, request -> request, httpResponse, responseTimes);
  }

  private void expectRequest(String method,
      String path, RequestCustomizer requestCustomizer,
      HttpResponse httpResponse) {
    expectRequest(method, path, requestCustomizer, httpResponse, 1);
  }

  private void expectRequest(String method,
      String path, RequestCustomizer requestCustomizer,
      HttpResponse httpResponse, int responseTimes) {
    client
        .when(
            requestCustomizer.customize(
                request()
                    .withMethod(method)
                    .withPath(path)
            )
            ,
            Times.exactly(responseTimes)
        )
        .respond(
            httpResponse
        );
  }

}
