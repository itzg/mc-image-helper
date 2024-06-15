package me.itzg.helpers.paper;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.Arrays;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.paper.PaperDownloadsClient.VersionBuild;
import me.itzg.helpers.paper.model.BuildInfo;
import me.itzg.helpers.paper.model.ProjectInfo;
import me.itzg.helpers.paper.model.ReleaseChannel;
import me.itzg.helpers.paper.model.VersionBuilds;
import org.junit.jupiter.api.Test;

@WireMockTest
class PaperDownloadsClientTest {

    @Test
    void getLatestBuild(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/v2/projects/paper/versions/1.20.6/builds")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("paper/version-builds-response_mix_default_latest.json")
            )
        );

        final Integer result;
        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder().build()
        )) {

            result = client.getLatestBuild("paper", "1.20.6", ReleaseChannel.DEFAULT)
                .block();
        }

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(140);
    }

    @Test
    void getLatestBuild_butNoMatchingChannel(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/v2/projects/paper/versions/1.20.5/builds")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("paper/version-builds-response_only_experimental.json")
            )
        );

        final Integer result;
        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder().build()
        )) {

            result = client.getLatestBuild("paper", "1.20.5", ReleaseChannel.DEFAULT)
                .block();
        }

        assertThat(result).isNull();
    }

    @Test
    void getLatestVersionBuild_withExperimentalThenMix(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/v2/projects/paper/versions/experiments/builds")
            .willReturn(
                okForJson(
                    new VersionBuilds()
                        .setVersion("experiments")
                        .setBuilds(Arrays.asList(
                            new BuildInfo()
                                .setBuild(1)
                                .setChannel(ReleaseChannel.EXPERIMENTAL),
                            new BuildInfo()
                                .setBuild(2)
                                .setChannel(ReleaseChannel.EXPERIMENTAL)
                        ))
                )
            )
        );

        stubFor(get("/v2/projects/paper/versions/mix/builds")
            .willReturn(
                okForJson(
                    new VersionBuilds()
                        .setVersion("mix")
                        .setBuilds(Arrays.asList(
                            new BuildInfo()
                                .setBuild(1)
                                .setChannel(ReleaseChannel.EXPERIMENTAL),
                            new BuildInfo()
                                .setBuild(2)
                                .setChannel(ReleaseChannel.DEFAULT),
                            new BuildInfo()
                                .setBuild(3)
                                .setChannel(ReleaseChannel.EXPERIMENTAL)
                        ))
                )
            )
        );

        stubFor(get("/v2/projects/paper")
            .willReturn(okForJson(
                new ProjectInfo()
                    .setVersions(Arrays.asList(
                        "oldest",
                        "older",
                        "mix",
                        "experiments"
                    ))
                )
            )
        );

        final VersionBuild result;
        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder().build()
        )) {

            result = client.getLatestVersionBuild("paper", ReleaseChannel.DEFAULT)
                .block();
        }

        assertThat(result).isNotNull();
        assertThat(result.getVersion()).isEqualTo("mix");
        assertThat(result.getBuild()).isEqualTo(2);
    }

    @Test
    void getLatestVersionBuild_wantsExperimental(WireMockRuntimeInfo wmInfo) {
        stubFor(get("/v2/projects/paper/versions/experiments/builds")
            .willReturn(
                okForJson(
                    new VersionBuilds()
                        .setVersion("experiments")
                        .setBuilds(Arrays.asList(
                            new BuildInfo()
                                .setBuild(1)
                                .setChannel(ReleaseChannel.EXPERIMENTAL),
                            new BuildInfo()
                                .setBuild(2)
                                .setChannel(ReleaseChannel.EXPERIMENTAL)
                        ))
                )
            )
        );

        stubFor(get("/v2/projects/paper")
            .willReturn(okForJson(
                new ProjectInfo()
                    .setVersions(Arrays.asList(
                        "oldest",
                        "older",
                        "mix",
                        "experiments"
                    ))
                )
            )
        );

        final VersionBuild result;
        try (PaperDownloadsClient client = new PaperDownloadsClient(wmInfo.getHttpBaseUrl(),
            Options.builder().build()
        )) {

            result = client.getLatestVersionBuild("paper",
                    ReleaseChannel.EXPERIMENTAL)
                .block();
        }

        assertThat(result).isNotNull();
        assertThat(result.getVersion()).isEqualTo("experiments");
        assertThat(result.getBuild()).isEqualTo(2);
    }


}