package me.itzg.helpers.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static me.itzg.helpers.http.Fetch.fetch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
class SpecificFileFetchBuilderTest {

    @Test
    void handlesNotFound(WireMockRuntimeInfo wm, @TempDir Path tempDir) {
        stubFor(
            get(anyUrl())
                .willReturn(
                    notFound()
                )
        );

        final Path requestedOutputFile = tempDir.resolve("downloaded");

        assertThatThrownBy(
            fetch(URI.create(wm.getHttpBaseUrl() + "/file"))
            .toFile(requestedOutputFile)::execute
        )
            .isInstanceOf(FailedRequestException.class)
            .hasMessageContaining("404");

        assertThat(requestedOutputFile)
            .doesNotExist();
    }

    @Test
    void overwritesWhenNoConstraints(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final Path requestedOutputFile = tempDir.resolve("downloaded.txt");

        Files.write(requestedOutputFile, Collections.singletonList("original content"));

        stubFor(
            get("/requested.txt")
                .willReturn(
                    ok("new content")
                )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/requested.txt"))
            .toFile(requestedOutputFile)
            .execute();

        assertThat(result).isEqualTo(requestedOutputFile);

        assertThat(result)
            .exists()
            .hasContent("new content");
    }

    @Test
    void whenRequestSkipNotExists_butExists(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final Path requestedOutputFile = tempDir.resolve("downloaded.txt");

        Files.write(requestedOutputFile, Collections.singletonList("original content"));

        stubFor(
            get("/requested.txt")
                .willReturn(
                    ok("new content")
                )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/requested.txt"))
            .toFile(requestedOutputFile)
            .skipExisting(true)
            .execute();

        assertThat(result).isEqualTo(requestedOutputFile);

        assertThat(result)
            .exists()
            .hasContent("original content");
    }

    @Test
    void whenRequestSkipNotExists_butDisabled(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final Path requestedOutputFile = tempDir.resolve("mod.jar");
        final Path disabledFile = tempDir.resolve("mod.jar.disabled");

        Files.write(disabledFile, Collections.singletonList("original content"));

        stubFor(
            get("/mod.jar")
                .willReturn(
                    ok("new content")
                )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/mod.jar"))
            .toFile(requestedOutputFile)
            .skipExisting(true)
            .execute();

        assertThat(result).isEqualTo(disabledFile);
        assertThat(disabledFile).hasContent("original content");
        assertThat(requestedOutputFile).doesNotExist();
        verify(0, getRequestedFor(urlEqualTo("/mod.jar")));
    }

    @Test
    void disabledFileIgnoredWithoutSkipExisting(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        final Path requestedOutputFile = tempDir.resolve("mod.jar");
        final Path disabledFile = tempDir.resolve("mod.jar.disabled");

        Files.write(disabledFile, Collections.singletonList("original content"));

        stubFor(
            get("/mod.jar")
                .willReturn(
                    ok("new content")
                )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/mod.jar"))
            .toFile(requestedOutputFile)
            .execute();

        assertThat(result).isEqualTo(requestedOutputFile);
        assertThat(requestedOutputFile).hasContent("new content");
    }

}
