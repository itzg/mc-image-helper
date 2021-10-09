package me.itzg.helpers.get;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.MediaType;
import picocli.CommandLine;

@ExtendWith(MockServerExtension.class)
class GetCommandTest {

  @Test
  void outputsDownload(ClientAndServer client) throws MalformedURLException {
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
                new URL("http", "localhost", client.getLocalPort(), "/outputsDownload.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Response content");
  }

  @Test
  void downloadsToFile(ClientAndServer client, @TempDir Path tempDir) throws MalformedURLException {
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
                new URL("http", "localhost", client.getLocalPort(), "/downloadsToFile.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(expectedFile).exists();
    assertThat(expectedFile).hasContent("Response content to file");
  }
}