package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.modrinth.model.Version;
import org.junit.jupiter.api.Test;

@WireMockTest
class ModrinthApiClientTest {

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