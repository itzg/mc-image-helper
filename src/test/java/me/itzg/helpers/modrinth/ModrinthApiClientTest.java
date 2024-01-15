package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import java.util.stream.Stream;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.ServerSide;
import me.itzg.helpers.modrinth.model.Version;
import org.junit.jupiter.api.Test;

@WireMockTest
class ModrinthApiClientTest {

    @Test
    void getBulkProjectsWithUnknownServerSide(WireMockRuntimeInfo wmInfo) {
        stubFor(
            get(urlPathEqualTo("/v2/projects"))
            .withQueryParam("ids", equalTo("[\"dynmap\"]"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("modrinth/project-dynmap-bad-server-side.json")
            )
        );

        try (ModrinthApiClient client = new ModrinthApiClient(wmInfo.getHttpBaseUrl(), "modrinth", Options.builder().build())) {
            final List<ResolvedProject> resp = client.bulkGetProjects(Stream.of(ProjectRef.parse("dynmap:beta")))
                .block();

            assertThat(resp).isNotEmpty();
            assertThat(resp).hasSize(1);

            final Project project = resp.get(0).getProject();

            assertThat(project).isNotNull();
            assertThat(project.getServerSide()).isEqualTo(ServerSide.unknown);
            assertThat(project.getId()).isEqualTo("fRQREgAc");
        }
    }

    @Test
    void getVersionsForProject(WireMockRuntimeInfo wmInfo) {

        stubFor(get(urlPathMatching("/v2/project/(BITzwT7B|clickvillagers)/version"))
            .withQueryParam("loaders", equalTo("[\"purpur\",\"paper\",\"spigot\"]"))
            .withQueryParam("game_versions", equalTo("[\"1.20.1\"]"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("modrinth/project-BITzwT7B-version-resp.json")
            )
        );

        try (ModrinthApiClient client = new ModrinthApiClient(wmInfo.getHttpBaseUrl(), "modrinth", Options.builder().build())) {
            final List<Version> result = client.getVersionsForProject("BITzwT7B", Loader.purpur, "1.20.1")
                .block();

            assertThat(result)
                .hasSize(3)
                .extracting(Version::getId)
                .containsExactly(
                    "O9nndrTu",
                    "DfUyEmsH",
                    "oUJMLDhz"
                );
        }
    }

}