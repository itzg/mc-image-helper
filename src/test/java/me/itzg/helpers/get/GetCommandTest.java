package me.itzg.helpers.get;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import picocli.CommandLine;

@ExtendWith(MockServerExtension.class)
class GetCommandTest {
  private final ClientAndServer client;
  private final MockServerSupport mock;

  GetCommandTest(ClientAndServer client) {
    this.client = client;
    this.mock = new MockServerSupport(client);
  }

  @AfterEach
  void tearDown() {
    client.reset();
    TestLoggingAppender.reset();
  }

  @Test
  void outputsDownload() throws MalformedURLException {
    mock.expectRequest("GET","/outputsDownload.txt",
        response()
            .withBody("Response content", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                mock.buildMockedUrl("/outputsDownload.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Response content");
  }

  @Test
  void usesBasicAuth() throws MalformedURLException, URISyntaxException {
    mock.expectRequest("GET","/",
        request -> request.withHeader("Authorization", "Basic dXNlcjpwYXNz"),
        response()
            .withBody("You're in", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                mock.buildMockedUrl("/", "user:pass").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("You're in");
  }

  @Test
  void handlesExtraSlashAtStartOfPath() throws MalformedURLException {
    mock.expectRequest("GET","/handlesExtraSlashAtStartOfPath.txt",
        response()
            .withBody("Response content", MediaType.TEXT_PLAIN)
    );

    final StringWriter output = new StringWriter();
    final int status =
        new CommandLine(new GetCommand())
            .setOut(new PrintWriter(output))
            .execute(
                mock.buildMockedUrl("//handlesExtraSlashAtStartOfPath.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Response content");
  }

  @Test
  void handlesNotFound() throws MalformedURLException {
    mock.expectRequest("GET","/handlesNotFound",
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
                mock.buildMockedUrl("/handlesNotFound").toString()
            );

    assertThat(status).isEqualTo(1);
    assertThat(output.toString()).isEqualTo("");

  }

  @Test
  void handlesRetryOn403ThenSuccess() throws MalformedURLException {
    mock.expectRequest("GET","/handlesRetry",
        response()
            .withStatusCode(403)
            .withBody("Permission denied", MediaType.TEXT_PLAIN),
        1
    );
    mock.expectRequest("GET","/handlesRetry",
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
                mock.buildMockedUrl("/handlesRetry").toString()
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
    mock.expectRequest("GET","/handlesRetry",
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
                mock.buildMockedUrl("/handlesRetry").toString()
            );

    assertThat(status).isEqualTo(1);

    client.verify(
        request("/handlesRetry"),
        VerificationTimes.exactly(2)
    );
  }

  @Test
  void usesGivenAcceptHeader() throws MalformedURLException {
    mock.expectRequest("GET","/content.txt",
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
                mock.buildMockedUrl("/content.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Some text");
  }

  @Test
  void usesGivenApiKeyHeader() throws MalformedURLException {
    mock.expectRequest("GET","/content.txt",
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
                mock.buildMockedUrl("/content.txt").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("Some text");
  }


}
