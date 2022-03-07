package me.itzg.helpers.get;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpResponse.response;

import java.net.MalformedURLException;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@ExtendWith(MockServerExtension.class)
class ExistsTest {
  private final ClientAndServer client;
  private final MockServerSupport mock;

  ExistsTest(ClientAndServer client) {
    this.client = client;
    mock = new MockServerSupport(client);
  }

  @AfterEach
  void tearDown() {
    client.reset();
    TestLoggingAppender.reset();
  }


  @Test
  void okWhenExists() throws MalformedURLException {
    mock.expectRequest("HEAD", "/exists",
        response("Here!"));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "--exists",
                mock.buildMockedUrl("/exists").toString()
            );

    assertThat(status).isEqualTo(0);
  }

  @Test
  void notOkWhenOneMissing() throws MalformedURLException {
    mock.expectRequest("HEAD", "/exists",
        response("Here!"));
    mock.expectRequest("HEAD", "/notHere",
        response().withStatusCode(404));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "--exists",
                mock.buildMockedUrl("/exists").toString(),
                mock.buildMockedUrl("/notHere").toString()
            );

    assertThat(status).isEqualTo(ExitCode.SOFTWARE);
  }

  @Test
  void includesAcceptHeader() throws MalformedURLException {
    mock.expectRequest("HEAD", "/exists",
        request -> request.withHeader("accept", "text/plain"),
        response("Here!"));

    final int status =
        new CommandLine(new GetCommand())
            .execute(
                "--exists",
                "--accept", "text/plain",
                mock.buildMockedUrl("/exists").toString()
            );

    assertThat(status).isEqualTo(0);

  }
}
