package me.itzg.helpers.mvn;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MavenRepoApiTest {

    @TempDir
    Path tempDir;

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
            .usingFilesUnderClasspath("mvn")
        )
        .configureStaticDsl(true)
        .build();

    public static Stream<Arguments> fetchMetadataArgs() {
        return Stream.of(
            arguments(false, false),
            arguments(true, false),
            arguments(true, true)
        );
    }

    @ParameterizedTest
    @MethodSource("fetchMetadataArgs")
    void fetchMetadata(boolean withCaching, boolean simulateOldFile) {
        stubFor(get("/repository/release/link/infra/packwiz/packwiz-installer/maven-metadata.xml")
            .willReturn(aResponse()
                .withBodyFile("packwiz_maven-metadata.xml")
            )
        );

        try (SharedFetch sharedFetch = new SharedFetch("test", Options.builder().build())) {
            final MavenRepoApi api = new MavenRepoApi(wm.url("/repository/release"), sharedFetch)
                .setMetadataCacheDir(withCaching ? tempDir : null)
                .setInstantSource(() ->
                    simulateOldFile ?
                        Instant.now().plus(MavenRepoApi.MAX_CACHE_AGE).plus(Duration.ofMinutes(1))
                        : Instant.now()
                );

            // Retrieve multiple times to verify cache intercepted 2nd+ request
            for (int i = 0; i < 3; i++) {
                final MavenMetadata metadata = api.fetchMetadata("link.infra.packwiz", "packwiz-installer")
                    .block();

                assertThat(metadata).isNotNull();
                assertThat(metadata.getVersioning().getLatest()).isEqualTo("v0.5.12");
            }
        }

        verify(
            withCaching ? (simulateOldFile ? 3 : 1)
                : 3,
            getRequestedFor(urlEqualTo("/repository/release/link/infra/packwiz/packwiz-installer/maven-metadata.xml"))
        );
    }
}