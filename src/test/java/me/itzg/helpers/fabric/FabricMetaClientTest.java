package me.itzg.helpers.fabric;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import me.itzg.helpers.fabric.FabricMetaClient.InvalidJarException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The corrupt fixture is the first bytes of a real launcher jar: ZIP magic is present,
 * but the central directory is missing so the file is not a valid jar.
 */
@WireMockTest
class FabricMetaClientTest {

    private static final String LAUNCHER_PATH =
        "/v2/versions/loader/1.19.4/0.14.21/0.11.2/server/jar";
    private static final String LAUNCHER_FILENAME = "fabric-server-launcher.jar";
    private static final Path VALID_LAUNCHER =
        Paths.get("src/test/resources/__files/fabric-empty-launcher.jar");

    @TempDir
    Path tempDir;

    @Test
    void redownloadsOnceWhenLauncherIsNotAValidJar(WireMockRuntimeInfo wm) {
        stubLauncherHead();
        stubFor(get(urlEqualTo(LAUNCHER_PATH)).inScenario("corrupt-jar")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(launcherResponse().withBodyFile("fabric-corrupt-launcher.jar"))
        ).setNewScenarioState("valid");
        stubFor(get(urlEqualTo(LAUNCHER_PATH)).inScenario("corrupt-jar")
            .whenScenarioStateIs("valid")
            .willReturn(launcherResponse().withBodyFile("fabric-empty-launcher.jar"))
        );

        final Path launcher = download(wm);

        assertThat(launcher)
            .hasSameBinaryContentAs(VALID_LAUNCHER);
        verify(2, getRequestedFor(urlEqualTo(LAUNCHER_PATH)));
    }

    @Test
    void deletesCorruptLauncherAndStopsAfterOneRetry(WireMockRuntimeInfo wm) {
        stubLauncherHead();
        stubFor(get(urlEqualTo(LAUNCHER_PATH))
            .willReturn(launcherResponse().withBodyFile("fabric-corrupt-launcher.jar"))
        );

        assertThatThrownBy(() -> download(wm))
            .cause()
            .isInstanceOf(InvalidJarException.class)
            .hasMessageContaining("not a valid jar");

        assertThat(tempDir.resolve(LAUNCHER_FILENAME)).doesNotExist();
        verify(2, getRequestedFor(urlEqualTo(LAUNCHER_PATH)));
    }

    @Test
    void deletesHtmlLauncherAndStopsAfterOneRetry(WireMockRuntimeInfo wm) {
        stubLauncherHead();
        stubFor(get(urlEqualTo(LAUNCHER_PATH))
            .willReturn(launcherResponse().withBody("<html><body>Not found</body></html>"))
        );

        assertThatThrownBy(() -> download(wm))
            .cause()
            .isInstanceOf(InvalidJarException.class)
            .hasMessageContaining("not a valid jar");

        assertThat(tempDir.resolve(LAUNCHER_FILENAME)).doesNotExist();
        verify(2, getRequestedFor(urlEqualTo(LAUNCHER_PATH)));
    }

    @Test
    void deletesEmptyLauncherAndStopsAfterOneRetry(WireMockRuntimeInfo wm) {
        stubLauncherHead();
        stubFor(get(urlEqualTo(LAUNCHER_PATH))
            .willReturn(launcherResponse().withBody(""))
        );

        assertThatThrownBy(() -> download(wm))
            .cause()
            .isInstanceOf(InvalidJarException.class)
            .hasMessageContaining("not a valid jar");

        assertThat(tempDir.resolve(LAUNCHER_FILENAME)).doesNotExist();
        verify(2, getRequestedFor(urlEqualTo(LAUNCHER_PATH)));
    }

    private Path download(WireMockRuntimeInfo wm) {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("fabric", Options.builder().build())) {
            final FabricMetaClient client = new FabricMetaClient(sharedFetch, wm.getHttpBaseUrl());
            client.setDownloadRetryMinBackoff(Duration.ofMillis(1));
            return client.downloadLauncher(
                    tempDir,
                    "1.19.4",
                    "0.14.21",
                    "0.11.2",
                    (status, uri, file) -> { },
                    false
                )
                .block(Duration.ofSeconds(20));
        }
    }

    private static void stubLauncherHead() {
        stubFor(head(urlEqualTo(LAUNCHER_PATH))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(CONTENT_DISPOSITION, "attachment; filename=\"" + LAUNCHER_FILENAME + "\"")
                .withHeader(CONTENT_TYPE, "application/java-archive")
            )
        );
    }

    private static ResponseDefinitionBuilder launcherResponse() {
        return aResponse()
            .withStatus(200)
            .withHeader(CONTENT_DISPOSITION, "attachment; filename=\"" + LAUNCHER_FILENAME + "\"")
            .withHeader(CONTENT_TYPE, "application/java-archive");
    }
}
