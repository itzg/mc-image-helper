package me.itzg.helpers.forge;

import static me.itzg.helpers.forge.ProvidedInstallerResolver.INSTALLER_ID_CLEANROOM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

        final VersionPair versions = resolver.resolve(null);

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
            arguments("version-forge-1.20.2.json", "1.20.2", "48.1.0"),
            arguments("version-forge-1.12.2.json", "1.12.2", "14.23.5.2860"),
            arguments("version-cleanroom.json", "1.12.2", "0.2.4-alpha")
        );
    }


    @ParameterizedTest
    @MethodSource("extractsFromVersionJsonArgs")
    void extractsFromVersionJson(String json, String expectedMinecraftVersion, String expectedForgeVersion, String expectedVariantOverride)
        throws IOException {
        final VersionPair result = ProvidedInstallerResolver.extractFromVersionJson(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(result).isNotNull();
        assertThat(result.minecraft).isEqualTo(expectedMinecraftVersion);
        assertThat(result.forge).isEqualTo(expectedForgeVersion);
        assertThat(result.variantOverride).isEqualTo(expectedVariantOverride);
    }

    public static Stream<Arguments> extractsFromVersionJsonArgs() {
        /*
         To obtain the test JSON:
         - download an installer from
            - https://neoforged.net/page/2/
            - https://github.com/CleanroomMC/Cleanroom/releases
         - open the version.json from it
         - copy the initial fields from it: id ... inheritsFrom
         */
        return Stream.of(
            arguments("""
                {"id": "neoforge-21.11.42",
                  "time": "2026-03-28T12:57:58.675623769",
                  "releaseTime": "2026-03-28T12:57:58.675623769",
                  "type": "release",
                  "mainClass": "net.neoforged.fml.startup.Client",
                  "inheritsFrom": "1.21.11"}""",
                "1.21.11", "21.11.42", null
                ),
            arguments("""
                {"id": "neoforge-26.1.2.70-beta",
                   "time": "2026-05-31T06:59:24.892763557",
                   "releaseTime": "2026-05-31T06:59:24.892763557",
                   "type": "release",
                   "mainClass": "net.neoforged.fml.startup.Client",
                   "inheritsFrom": "26.1.2"}""",
                "26.1.2", "26.1.2.70-beta", null
                ),
            arguments("""
                {"id": "cleanroom-0.5.12-alpha",
                    "time": "2026-05-09T03:52:36+00:00",
                    "releaseTime": "2026-05-09T03:52:36+00:00",
                    "type": "release",
                    "mainClass": "top.outlands.foundation.boot.Foundation",
                    "inheritsFrom": "1.12.2"}""",
                "1.12.2", "0.5.12-alpha", INSTALLER_ID_CLEANROOM)
        );
    }


}