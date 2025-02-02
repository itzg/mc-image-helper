package me.itzg.helpers.forge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProvidedInstallerResolverTest {

    @Test
    void resolvesVersionFromFile() throws URISyntaxException {
        final URL installerUrl = ProvidedInstallerResolverTest.class.getResource("/forge/forge-1.20.2-48.1.0-installer-trimmed.jar");
        assertThat(installerUrl).isNotNull();

        final ProvidedInstallerResolver resolver = new ProvidedInstallerResolver(
            Paths.get(installerUrl.toURI()));

        final VersionPair versions = resolver.resolve();

        assertThat(versions.minecraft).isEqualTo("1.20.2");
        assertThat(versions.forge).isEqualTo("48.1.0");
    }

    @ParameterizedTest
    @MethodSource("resolvesIdVariantsArgs")
    void resolvesIdVariants(String versionJsonName, String expectedMinecraftVersion, String expectedInstallerVersion)
        throws IOException {
        try (InputStream versionJsonStream = ProvidedInstallerResolverTest.class.getResourceAsStream(
            "/forge/" + versionJsonName)) {
            assertThat(versionJsonStream).isNotNull();

            final VersionPair result = ProvidedInstallerResolver.extractFromVersionJson(versionJsonStream);
            assertThat(result).isNotNull();
            assertThat(result.minecraft).isEqualTo(expectedMinecraftVersion);
            assertThat(result.forge).isEqualTo(expectedInstallerVersion);
        }
    }

    public static Stream<Arguments> resolvesIdVariantsArgs() {
        return Stream.of(
            Arguments.arguments("version-forge-1.20.2.json", "1.20.2", "48.1.0"),
            Arguments.arguments("version-forge-1.12.2.json", "1.12.2", "14.23.5.2860"),
            Arguments.arguments("version-cleanroom.json", "1.12.2", "0.2.4-alpha")
        );
    }

}