package me.itzg.helpers.curseforge;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import me.itzg.helpers.cache.ApiCachingDisabled;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.http.SharedFetch.Options;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

@WireMockTest
class CurseForgeApiClientTest {

    @Test
    void apiKeyHeaderIsTrimmed(WireMockRuntimeInfo wmInfo) {
        @Language("JSON") final String body = "{\"data\": []}";
        stubFor(get("/v1/categories?gameId=test&classesOnly=true")
            .willReturn(aResponse()
                .withBody(body)
                .withHeader("Content-Type", "application/json")
            )
        );

        final CategoryInfo result;
        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "key\n", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            result = client.loadCategoryInfo(Collections.singleton("mc-mods"))
                .block();
        }

        assertThat(result).isNotNull();

        verify(getRequestedFor(urlEqualTo("/v1/categories?gameId=test&classesOnly=true"))
            .withHeader("x-api-key", equalTo("key"))
        );
    }

    @Test
    void unknownModDoesNotPreventResolvingOthers(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlPathEqualTo("/v1/mods/search"))
            .withQueryParam("slug", equalTo("unknown-mod"))
            .willReturn(jsonResponse("{\"data\": []}", 200))
        );
        stubFor(get(urlPathEqualTo("/v1/mods/search"))
            .withQueryParam("slug", equalTo("known-mod"))
            .willReturn(jsonResponse("{\"data\": [{\"id\": 100, \"classId\": 6, \"gameId\": 432}]}", 200))
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(),
            "key", Options.builder().build(), "432", new ApiCachingDisabled()
        )) {
            final Set<Integer> ids = Flux.just("unknown-mod", "known-mod")
                .flatMap(slug -> client.searchMod(slug, 6)
                    .map(CurseForgeMod::getId)
                    .onErrorComplete(UnknownModException.class)
                )
                .collect(Collectors.toSet())
                .block();

            assertThat(ids).containsExactly(100);
        }
    }

    @Test
    void fallbackUrlEncodesSpaces(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        final CurseForgeFile cfFile = new CurseForgeFile();
        cfFile.setId(5228909);
        cfFile.setGameId(432);
        cfFile.setModId(551909);
        cfFile.setDownloadUrl(null);
        final String fileName = "Butchersdelight beta 1.20.1 2.1.0.jar";
        cfFile.setFileName(fileName);

        stubFor(get(urlPathEqualTo("/files/5228/909/Butchersdelight%20beta%201.20.1%202.1.0.jar"))
            .willReturn(aResponse()
                .withBody("file content")
                .withHeader("Content-Type", "application/java-archive")
            )
        );

        // observed in dev tools https://mediafilez.forgecdn.net/files/5228/909/Butchersdelight%20beta%201.20.1%202.1.0.jar
        final Path result = new CurseForgeApiClient(wmInfo.getHttpBaseUrl()+"/api", "key", Options.builder().build(), "432",
            new ApiCachingDisabled(), wmInfo.getHttpBaseUrl()
        )
            .download(cfFile, tempDir.resolve(fileName), (status, uri, file) -> {})
            .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();

        assertThat(tempDir).isNotEmptyDirectory();
        assertThat(tempDir.resolve(fileName))
            .exists()
            .content().isEqualTo("file content");
    }
}