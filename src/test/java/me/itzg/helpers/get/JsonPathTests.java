package me.itzg.helpers.get;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

@WireMockTest
class JsonPathTests {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void stringField(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/string.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                        .put("field", "a string")
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--json-path", "$.field",
                    wm.getHttpBaseUrl() + "/string.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("a string" + lineSeparator());
    }

    @Test
    void handlesJqStylePath(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/string.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                        .put("field", "a string")
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--json-path", ".field",
                    wm.getHttpBaseUrl() + "/string.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("a string" + lineSeparator());
    }

    @Test
    void numberField(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/number.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                        .put("field", 543)
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--json-path", "$.field",
                    wm.getHttpBaseUrl() + "/number.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("543" + lineSeparator());
    }

    @Test
    void booleanField(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/boolean.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                        .put("field", true)
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--json-path", "$.field",
                    wm.getHttpBaseUrl() + "/boolean.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("true" + lineSeparator());
    }

    @Test
    void handlesMissingField_defaultOutputNull(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/content.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                    // empty
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter stdout = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(stdout))
                .execute(
                    "--json-path", "$.field",
                    wm.getHttpBaseUrl() + "/content.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(stdout.toString()).isEqualTo("null" + lineSeparator());
        assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    @Test
    void handlesMissingIntermediateField(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/content.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                        .putObject("downloads")
                        .putObject("client")
                        .put("url", "https://launcher.mojang.com/v1/objects/b679fea27f2284836202e9365e13a82552092e5d/client.jar")
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter stdout = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(stdout))
                .execute(
                    "--json-path", "$.downloads.server.url",
                    wm.getHttpBaseUrl() + "/content.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(stdout.toString()).isEqualTo("null" + lineSeparator());
        assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    @Test
    void handlesMissingField_alternateValue(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/content.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                    // empty
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter stdout = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(stdout))
                .execute(
                    "--json-path", "$.field",
                    "--json-value-when-missing", "missing",
                    wm.getHttpBaseUrl() + "/content.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(stdout.toString()).isEqualTo("missing" + lineSeparator());
        assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    /**
     * This test case captures the case of using forge's promotions_slim.json where a "{version}-recommended" entry is not
     * present.
     */
    @Test
    void handlesMissingField_errorWhenEmptyValue(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/content.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(
                    mapper.createObjectNode()
                        .putObject("promos")
                        .put("1.17.1-latest", "37.0.95")
                )
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter stdout = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(stdout))
                .execute(
                    "--json-path", "$.promos['1.17.1-recommended']",
                    "--json-value-when-missing", "",
                    wm.getHttpBaseUrl() + "/content.json"
                );

        assertThat(status).isEqualTo(1);
        assertThat(stdout.toString()).isEqualTo("");
        assertThat(TestLoggingAppender.getEvents()).isEmpty();
    }

    @Test
    void useConcatWithListField(WireMockRuntimeInfo wm) throws MalformedURLException {
        final ObjectNode rootNode = mapper.createObjectNode();
        rootNode.putArray("field")
            .add("one").add("two");

        stubFor(get(urlPathEqualTo("/content.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withJsonBody(rootNode)
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--json-path", "$.field.concat()",
                    wm.getHttpBaseUrl() + "/content.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("onetwo" + lineSeparator());
    }

    @ParameterizedTest
    @MethodSource("argsForJsonPathQuery")
    void supportsQueryOfStringValues(String content, String expected, WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(get(urlPathEqualTo("/content.json"))
            .withHeader("accept", equalTo("application/json"))
            .willReturn(aResponse()
                .withBody(content)
                .withHeader("Content-Type", "application/json")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--json-path", "$.values[?(@.name == 'one')].value",
                    wm.getHttpBaseUrl() + "/content.json"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo(expected + lineSeparator());

    }


    @SuppressWarnings("unused")
    static Arguments[] argsForJsonPathQuery() {
        return new Arguments[]{
            arguments(
                "{\"values\": [{\"value\": \"v1\", \"name\": \"one\"}, {\"value\": \"v2\", \"name\": \"two\"}]}",
                "v1"
            ),
            arguments(
                "{\"values\": [{\"value\": 1, \"name\": \"one\"}, {\"value\": 1, \"name\": \"two\"}]}",
                "1"
            ),
            arguments(
                "{\"values\": [{\"value\": true, \"name\": \"one\"}, {\"value\": false, \"name\": \"two\"}]}",
                "true"
            )
        };
    }

}
