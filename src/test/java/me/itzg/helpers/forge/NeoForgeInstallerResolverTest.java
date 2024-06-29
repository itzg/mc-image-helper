package me.itzg.helpers.forge;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.stream.Stream;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@WireMockTest
class NeoForgeInstallerResolverTest {

    public static Stream<Arguments> resolve_args() {
        return Stream.of(
            arguments("1.20.4", "beta", "1.20.4", "20.4.62-beta"),
            arguments("1.20.4", "latest", null, null),
            arguments("1.20.2", "latest", "1.20.2", "20.2.88"),
            arguments("1.20.2", "beta", "1.20.2", "20.2.88"),
            arguments("1.20.3", "beta", "1.20.3", "20.3.8-beta"),
            arguments("latest", "20.2.85-beta", "1.20.2", "20.2.85-beta"),
            arguments("latest", "20.2.88", "1.20.2", "20.2.88"),
            arguments("1.20.1", "latest", "1.20.1", "47.1.84"),
            arguments("1.21", "beta", "1.21", "21.0.42-beta")
        );
    }

    @ParameterizedTest
    @MethodSource("resolve_args")
    void resolve(String minecraftVersion, String neoforgeVersion,
        String expectedMinecraftVersion, String expectedNeoforgeVersion,
        WireMockRuntimeInfo wmInfo
    ) {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-neoforge", Options.builder().build())) {
            final NeoForgeInstallerResolver resolver = new NeoForgeInstallerResolver(
                sharedFetch,
                minecraftVersion, neoforgeVersion,
                wmInfo.getHttpBaseUrl()
            );

            stubFor(get("/net/neoforged/neoforge/maven-metadata.xml")
                .willReturn(aResponse()
                    .withBodyFile("forge/neoforged-neoforge-maven-metadata.xml")
                )
            );
            stubFor(get("/net/neoforged/forge/maven-metadata.xml")
                .willReturn(aResponse()
                    .withBodyFile("forge/neoforged-forge-maven-metadata.xml")
                )
            );

            final VersionPair versionPair = resolver.resolve();
            if (expectedNeoforgeVersion == null) {
                assertThat(versionPair).isNull();
            }
            else {
                assertThat(versionPair).isNotNull();
                assertThat(versionPair.minecraft).isEqualTo(expectedMinecraftVersion);
                assertThat(versionPair.forge).isEqualTo(expectedNeoforgeVersion);
            }

        }
    }
}