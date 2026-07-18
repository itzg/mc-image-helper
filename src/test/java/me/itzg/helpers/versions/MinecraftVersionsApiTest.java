package me.itzg.helpers.versions;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;
import java.net.URI;

class MinecraftVersionsApiTest {
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .usingFilesUnderClasspath("versions")
            .extensions(new ResponseTemplateTransformer(false)))
        .build();

    @ParameterizedTest
    @CsvSource({
        "release,26.2",
        "latest,26.2",
        "snapshot,26.3-snapshot-3",
        "26.2,26.2",
        "1.8,",
    })
    void testResolve(String input, String expected) {
        final MinecraftVersionsApi api = new MinecraftVersionsApi(new SharedFetch("test-minecraft-versions-api", Options.builder().build()))
            .setManifestUrl(URI.create(wm.baseUrl() + "/mc/game/version_manifest_v2.json"));

        final Mono<MinecraftVersionInfo> info = api.resolve(input);
        if (expected == null) {
            Assertions.assertThrows(InvalidParameterException.class, info::block);
        }
        else {
            final MinecraftVersionInfo resolved = info.block();
            Assertions.assertEquals(expected, resolved.getVersion());
        }
    }
}
