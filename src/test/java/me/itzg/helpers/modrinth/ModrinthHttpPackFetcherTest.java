package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import me.itzg.helpers.http.SharedFetchArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
public class ModrinthHttpPackFetcherTest {
    @Test
    void fetchesMrpackViaHttp(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws URISyntaxException {
        String modpackUrlPath = "/files/modpack/test.mrpack";
        String expectedModpackData = "test modpack data";

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "unit-test", new SharedFetchArgs().options());

        stubFor(get(modpackUrlPath).willReturn(ok().withBody(expectedModpackData)));

        ModrinthHttpPackFetcher fetcherUT = new ModrinthHttpPackFetcher(
            apiClient, tempDir, new URI(wm.getHttpBaseUrl() + modpackUrlPath));

        final FetchedPack fetchedPack = fetcherUT.fetchModpack(null).block();
        assertThat(fetchedPack).isNotNull();
        assertThat(fetchedPack.getMrPackFile()).content()
            .isEqualTo(expectedModpackData);
        assertThat(fetchedPack.getProjectSlug()).isNotBlank();
        assertThat(fetchedPack.getVersionId()).isNotBlank();
    }
}
