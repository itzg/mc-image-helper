package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WireMockTest
public class TestModrinthHttpPackFetcher {
    @Test
    void fetchesMrpackViaHttp(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws URISyntaxException, IOException
    {
        String modpackUrlPath = "/files/modpack/test.mrpack";
        String expectedModpackData = "test modpack data";

        SharedFetch sharedFetch = 
            new SharedFetch("unit-test", new SharedFetchArgs().options());

        stubFor(get(modpackUrlPath).willReturn(ok().withBody(expectedModpackData)));

        ModrinthHttpPackFetcher fetcherUT = new ModrinthHttpPackFetcher(
            sharedFetch, tempDir, new URI(wm.getHttpBaseUrl() + modpackUrlPath));

        Path mrpackFile = fetcherUT.fetchModpack(null).block();
        String actualModpackData = new String(Files.readAllBytes(mrpackFile));

        assertEquals(new String(expectedModpackData), actualModpackData);
    }
}
