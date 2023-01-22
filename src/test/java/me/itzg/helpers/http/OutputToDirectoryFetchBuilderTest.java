package me.itzg.helpers.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static me.itzg.helpers.http.Fetch.fetch;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
class OutputToDirectoryFetchBuilderTest {

    @Test
    void basicScenario(WireMockRuntimeInfo wm, @TempDir Path tempDir) throws IOException {
        stubFor(
            head(WireMock.urlPathEqualTo("/file"))
                .willReturn(
                    ok()
                        .withHeader("content-disposition", "attachment; filename=\"actual.txt\"")
                )
        );
        stubFor(
            get("/file")
                .willReturn(
                    ok("content of actual.txt")
                )
        );

        final Path result = fetch(URI.create(wm.getHttpBaseUrl() + "/file"))
            .toDirectory(tempDir)
            .execute();

        final Path expectedFile = tempDir.resolve("actual.txt");

        assertThat(result)
            .isEqualTo(expectedFile)
            .exists()
            .hasContent("content of actual.txt");
    }
}