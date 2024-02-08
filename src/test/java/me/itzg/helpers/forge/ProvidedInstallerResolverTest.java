package me.itzg.helpers.forge;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

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
}