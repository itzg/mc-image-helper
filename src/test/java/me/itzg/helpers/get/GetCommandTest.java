package me.itzg.helpers.get;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@WireMockTest(httpsEnabled = true)
class GetCommandTest {

    @AfterEach
    void tearDown() {
        TestLoggingAppender.reset();
    }

    @Test
    void outputsDownload(WireMockRuntimeInfo wmInfo) throws MalformedURLException {
        stubFor(get("/outputsDownload.txt")
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Response content")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    wmInfo.getHttpBaseUrl() + "/outputsDownload.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("Response content");
    }

    @Test
    void usesBasicAuth(WireMockRuntimeInfo wmInfo) throws MalformedURLException, URISyntaxException {
        stubFor(get("/").withBasicAuth("user", "pass")
            .willReturn(aResponse()
                .withBody("You're in")
            )
        );

        final URI baseUri = URI.create(wmInfo.getHttpBaseUrl());

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    new URI(baseUri.getScheme(), "user:pass", baseUri.getHost(), baseUri.getPort(), "/", null, null).toString()
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("You're in");
    }

    @Test
    void handlesExtraSlashAtStartOfPath(WireMockRuntimeInfo wmInfo) throws MalformedURLException {
        stubFor(get("/handlesExtraSlashAtStartOfPath.txt")
            .willReturn(aResponse()
                .withBody("Response content")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    wmInfo.getHttpBaseUrl() + "//handlesExtraSlashAtStartOfPath.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("Response content");
    }

    @Test
    void handlesNotFound(WireMockRuntimeInfo wmInfo) throws MalformedURLException {
        stubFor(get("/handlesNotFound")
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body>Not found</body></html>")
            )
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    wmInfo.getHttpBaseUrl() + "/handlesNotFound"
                );

        assertThat(status).isEqualTo(1);
        assertThat(output.toString()).isEqualTo("");

    }

    @Test
    void handlesRetryOn403ThenSuccess(WireMockRuntimeInfo wmInfo) throws MalformedURLException {
        stubFor(get("/handlesRetry").inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(403))
        )
            .setNewScenarioState("did 403");
        stubFor(get("/handlesRetry").inScenario("retry")
            .whenScenarioStateIs("did 403")
            .willReturn(aResponse().withBody("Success"))
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--retry-count=1",
                    "--retry-delay=1",
                    wmInfo.getHttpBaseUrl() + "/handlesRetry"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("Success");

        verify(2, getRequestedFor(urlEqualTo("/handlesRetry")));
    }

    @Test
    void handlesRetryThenFails(WireMockRuntimeInfo wmInfo) throws MalformedURLException {
        stubFor(get("/handlesRetry")
            .willReturn(aResponse().withStatus(403))
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--retry-count=1",
                    "--retry-delay=1",
                    wmInfo.getHttpBaseUrl() + "/handlesRetry"
                );

        assertThat(status).isEqualTo(1);

        verify(2, getRequestedFor(urlEqualTo("/handlesRetry")));
    }

    @Test
    void usesGivenAcceptHeader(WireMockRuntimeInfo wmInfo) throws MalformedURLException {
        stubFor(get("/content.txt")
            .withHeader("accept", equalTo("text/plain"))
            .willReturn(aResponse().withBody("Some text"))
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--accept", "text/plain",
                    wmInfo.getHttpBaseUrl() + "/content.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("Some text");
    }

    @Test
    void usesGivenApiKeyHeader(WireMockRuntimeInfo wmInfo) throws MalformedURLException {
        stubFor(get("/content.txt")
            .withHeader("x-api-key", equalTo("xxxxxx"))
            .willReturn(aResponse().withBody("Some text"))
        );

        final StringWriter output = new StringWriter();
        final int status =
            new CommandLine(new GetCommand())
                .setOut(new PrintWriter(output))
                .execute(
                    "--apikey", "xxxxxx",
                    wmInfo.getHttpBaseUrl() + "/content.txt"
                );

        assertThat(status).isEqualTo(0);
        assertThat(output.toString()).isEqualTo("Some text");
    }

    @Test
    void tryHttps(WireMockRuntimeInfo wmInfo) throws Exception {
        stubFor(get("/content.txt")
            .willReturn(aResponse().withBody("Content"))
        );

        final String err = tapSystemErrNormalized(() -> {
            final int exitCode = new CommandLine(new GetCommand())
                .execute(
                    wmInfo.getHttpsBaseUrl() + "/content.txt"
                );

            assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
        });

        // Confirms it got far enough to establish a connection and didn't like
        // self-signed cert from WireMock. Really should add support in the get
        // command for "accept all certs".
        // See https://wiremock.org/docs/https/#common-https-issues
        assertThat(err).contains("unable to find valid certification path to requested target");
    }
}
