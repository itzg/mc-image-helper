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
import me.itzg.helpers.modrinth.model.VersionType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
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

    @Nested
    class resolveProjectVersion {

        @Test
        void latestExists(WireMockRuntimeInfo wmInfo) {

            stubFor(get(urlPathMatching("/v2/project/(fALzjamp|chunky)/version"))
                .withQueryParam("loaders", equalTo("[\"fabric\"]"))
                .withQueryParam("game_versions", equalTo("[\"1.21.1\"]"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("modrinth/project-version-chunky-fabric-1.21.1.json")
                )
            );

            try (ModrinthApiClient client = new ModrinthApiClient(wmInfo.getHttpBaseUrl(), "modrinth",
                Options.builder().build()
            )) {
                final Version result = client.resolveProjectVersion(project("fALzjamp", "chunky"), ProjectRef.parse("chunky"),
                        Loader.fabric,
                        "1.21.1", VersionType.release
                    )
                    .block();

                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo("dPliWter");
            }
        }

        private Project project(String id, String title) {
            return new Project()
                .setId(id)
                .setTitle(title);
        }

        @Test
        void noFiles(WireMockRuntimeInfo wmInfo) {
            stubFor(get(urlPathMatching("/v2/project/(3wmN97b8|multiverse-core)/version"))
                .withQueryParam("loaders", equalTo("[\"fabric\"]"))
                .withQueryParam("game_versions", equalTo("[\"1.21.1\"]"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")
                )
            );
            stubFor(get(urlPathEqualTo("/v2/project/3wmN97b8"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"title\": \"Multiverse-Core\"}")
                )
            );

            try (ModrinthApiClient client = new ModrinthApiClient(wmInfo.getHttpBaseUrl(), "modrinth",
                Options.builder().build()
            )) {
                Assertions.assertThatThrownBy(() ->
                        client.resolveProjectVersion(project("3wmN97b8", "multiverse-core"), ProjectRef.parse("multiverse-core"),
                                // mismatching loader type
                                Loader.fabric,
                                "1.21.1", VersionType.release
                            )
                            .block())
                    .isInstanceOf(NoFilesAvailableException.class);
            }

        }

        @Test
        void noApplicableVersionsOfType(WireMockRuntimeInfo wmInfo) {
            stubFor(get(urlPathMatching("/v2/project/(3wmN97b8|multiverse-core)/version"))
                .withQueryParam("loaders", equalTo("[\"purpur\",\"paper\",\"spigot\"]"))
                .withQueryParam("game_versions", equalTo("[\"1.21.1\"]"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("modrinth/project-version-only-beta.json")
                )
            );

            try (ModrinthApiClient client = new ModrinthApiClient(wmInfo.getHttpBaseUrl(), "modrinth",
                Options.builder().build()
            )) {
                Assertions.assertThatThrownBy(() ->
                        client.resolveProjectVersion(project("3wmN97b8", "multiverse-core"), ProjectRef.parse("multiverse-core"),
                                Loader.purpur,
                                "1.21.1", VersionType.release
                            )
                            .block())
                    .isInstanceOf(NoApplicableVersionsException.class);
            }

        }
    }
}