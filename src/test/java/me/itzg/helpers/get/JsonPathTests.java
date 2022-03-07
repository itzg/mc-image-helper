package me.itzg.helpers.get;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockserver.model.HttpResponse.response;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import me.itzg.helpers.TestLoggingAppender;
import me.itzg.helpers.get.MockServerSupport.RequestCustomizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.MediaType;
import picocli.CommandLine;

@ExtendWith(MockServerExtension.class)
class JsonPathTests {
  private final ClientAndServer client;
  private final MockServerSupport mock;

  JsonPathTests(ClientAndServer client) {
    this.client = client;
    mock = new MockServerSupport(client);
  }

  @AfterEach
  void tearDown() {
    client.reset();
    TestLoggingAppender.reset();
  }

  @Test
  void stringField() throws MalformedURLException {
    mock.expectRequest("GET", "/string.json",
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
                mock.buildMockedUrl("/string.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("a string" + lineSeparator());
  }

  private RequestCustomizer acceptJson() {
    return request -> request.withHeader("accept", "application/json");
  }

  @Test
  void handlesJqStylePath() throws MalformedURLException {
    mock.expectRequest("GET", "/string.json",
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
                mock.buildMockedUrl("/string.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("a string" + lineSeparator());
  }

  @Test
  void numberField() throws MalformedURLException {
    mock.expectRequest("GET", "/number.json",
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
                mock.buildMockedUrl("/number.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("543" + lineSeparator());
  }

  @Test
  void booleanField() throws MalformedURLException {
    mock.expectRequest("GET", "/boolean.json",
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
                mock.buildMockedUrl("/boolean.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("true" + lineSeparator());
  }

  @Test
  void handlesMissingField_defaultOutputNull() throws MalformedURLException {
    mock.expectRequest("GET", "/content.json",
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
                mock.buildMockedUrl("/content.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(stdout.toString()).isEqualTo("null" + lineSeparator());
    assertThat(TestLoggingAppender.getEvents()).isEmpty();
  }

  @Test
  void handlesMissingIntermediateField() throws MalformedURLException {
    mock.expectRequest("GET", "/content.json",
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
                mock.buildMockedUrl("/content.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(stdout.toString()).isEqualTo("null" + lineSeparator());
    assertThat(TestLoggingAppender.getEvents()).isEmpty();
  }

  @Test
  void handlesMissingField_alternateValue() throws MalformedURLException {
    mock.expectRequest("GET", "/content.json",
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
                mock.buildMockedUrl("/content.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(stdout.toString()).isEqualTo("missing" + lineSeparator());
    assertThat(TestLoggingAppender.getEvents()).isEmpty();
  }

  /**
   * This test case captures the case of using forge's promotions_slim.json where a
   * "{version}-recommended" entry is not present.
   */
  @Test
  void handlesMissingField_errorWhenEmptyValue() throws MalformedURLException {
    mock.expectRequest("GET", "/content.json",
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
                mock.buildMockedUrl("/content.json").toString()
            );

    assertThat(status).isEqualTo(1);
    assertThat(stdout.toString()).isEqualTo("");
    assertThat(TestLoggingAppender.getEvents()).isEmpty();
  }

  @Test
  void useConcatWithListField() throws MalformedURLException {
    mock.expectRequest("GET", "/content.json",
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
                mock.buildMockedUrl("/content.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("onetwo" + lineSeparator());
  }

  @ParameterizedTest
  @MethodSource("argsForJsonPathQuery")
  void supportsQueryOfStringValues(String content, String expected) throws MalformedURLException {
    mock.expectRequest("GET", "/content.json",
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
                mock.buildMockedUrl("/content.json").toString()
            );

    assertThat(status).isEqualTo(0);
    assertThat(output.toString()).isEqualTo(expected + lineSeparator());

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

}
