package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import me.itzg.helpers.http.SharedFetchArgs;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
public class ModrinthHttpPackFetcherTest {
    @Test
    void fetchesMrpackViaHttp(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws URISyntaxException, IOException
    {
        String modpackUrlPath = "/files/modpack/test.mrpack";
        String expectedModpackData = "test modpack data";

        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "unit-test", new SharedFetchArgs().options());

        stubFor(get(modpackUrlPath).willReturn(ok().withBody(expectedModpackData)));

        ModrinthHttpPackFetcher fetcherUT = new ModrinthHttpPackFetcher(
            apiClient, tempDir, new URI(wm.getHttpBaseUrl() + modpackUrlPath));

        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        assertThat(mrpackFile).content()
            .isEqualTo(new String(expectedModpackData));
    }
}
