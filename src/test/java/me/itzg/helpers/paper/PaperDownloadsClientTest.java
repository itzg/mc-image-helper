package me.itzg.helpers.paper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.URI;
import java.nio.file.Path;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.paper.PaperDownloadsClient.VersionBuild;
import me.itzg.helpers.paper.PaperDownloadsClient.VersionBuildFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

@WireMockTest
class PaperDownloadsClientTest {

    @Test
    void latestVersionBuild(WireMockRuntimeInfo wmInfo) {
        //TODO use urlPathTemplate with Wiremock 3.x
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("paper/v3/response_paper_project.json")
            )
        );

        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder().build()
        )) {
            final VersionBuild result = client.getLatestVersionBuild("paper")
                .block();

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo("1.21.6");
            assertThat(result.getBuild()).isEqualTo(46);
        }

    }

    @Test
    void latestBuild(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions/1.21.6/builds/latest"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("paper/v3/response_paper_build_response.json")
            )
        );

        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder().build()
        )) {
            final Integer buildId = client.getLatestBuild("paper", "1.21.6")
                .block();

            assertThat(buildId).isNotNull()
                .isEqualTo(46);
        }
    }

    @Test
    void downloadsSpecific(WireMockRuntimeInfo wmInfo, @TempDir Path tempDir) {
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions/1.21.6/builds/46"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("paper/v3/response_paper_build_response.json")
            )
        );

        stubFor(get(urlPathEqualTo("/v1/objects/bfca155b4a6b45644bfc1766f4e02a83c736e45fcc060e8788c71d6e7b3d56f6/paper-1.21.6-46.jar"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/java-archive")
                .withBody("some-jar-content")
            )
        );

        final FileDownloadStatusHandler statusHandler = Mockito.mock(FileDownloadStatusHandler.class);

        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder()
                .filesViaUrl(URI.create(wmInfo.getHttpBaseUrl()))
                .build()
        )) {
            final VersionBuildFile result = client.download("paper", tempDir, statusHandler, "1.21.6", 46)
                .block();

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo("1.21.6");
            assertThat(result.getBuild()).isEqualTo(46);
            assertThat(result.getFile()).hasFileName("paper-1.21.6-46.jar");

            final Path expectedFile = tempDir.resolve("paper-1.21.6-46.jar");
            assertThat(expectedFile)
                .exists()
                .hasContent("some-jar-content");
        }


    }

    @Test
    void downloadsLatest(WireMockRuntimeInfo wmInfo, @TempDir Path tempDir) {
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("paper/v3/response_paper_project.json")
            )
        );
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions/1.21.6/builds/46"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("paper/v3/response_paper_build_response.json")
            )
        );
        stubFor(get(urlPathEqualTo("/v1/objects/bfca155b4a6b45644bfc1766f4e02a83c736e45fcc060e8788c71d6e7b3d56f6/paper-1.21.6-46.jar"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/java-archive")
                .withBody("some-jar-content")
            )
        );

        final FileDownloadStatusHandler statusHandler = Mockito.mock(FileDownloadStatusHandler.class);

        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder()
                .filesViaUrl(URI.create(wmInfo.getHttpBaseUrl()))
                .build()
        )) {
            final VersionBuildFile result = client.downloadLatest("paper", tempDir, statusHandler)
                .block();

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo("1.21.6");
            assertThat(result.getBuild()).isEqualTo(46);
            assertThat(result.getFile()).hasFileName("paper-1.21.6-46.jar");

            final Path expectedFile = tempDir.resolve("paper-1.21.6-46.jar");
            assertThat(expectedFile)
                .exists()
                .hasContent("some-jar-content");
        }


    }

    @Test
    void downloadsLatestBuild(WireMockRuntimeInfo wmInfo, @TempDir Path tempDir) {
        stubFor(get(urlPathEqualTo("/v3/projects/paper/versions/1.21.6/builds/latest"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("paper/v3/response_paper_build_response.json")
            )
        );
        stubFor(get(urlPathEqualTo("/v1/objects/bfca155b4a6b45644bfc1766f4e02a83c736e45fcc060e8788c71d6e7b3d56f6/paper-1.21.6-46.jar"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/java-archive")
                .withBody("some-jar-content")
            )
        );

        final FileDownloadStatusHandler statusHandler = Mockito.mock(FileDownloadStatusHandler.class);

        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder()
                .filesViaUrl(URI.create(wmInfo.getHttpBaseUrl()))
                .build()
        )) {
            final VersionBuildFile result = client.downloadLatestBuild("paper", tempDir, statusHandler, "1.21.6")
                .block();

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo("1.21.6");
            assertThat(result.getBuild()).isEqualTo(46);
            assertThat(result.getFile()).hasFileName("paper-1.21.6-46.jar");

            final Path expectedFile = tempDir.resolve("paper-1.21.6-46.jar");
            assertThat(expectedFile)
                .exists()
                .hasContent("some-jar-content");
        }


    }
}