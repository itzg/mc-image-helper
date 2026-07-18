package me.itzg.helpers.vanilla;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import me.itzg.helpers.files.FileHashInvalidException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.versions.MinecraftVersionsApi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;

class VanillaInstallerTests {
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .usingFilesUnderClasspath("versions")
            .extensions(new ResponseTemplateTransformer(false)))
        .build();

    Path tempDir;
    Path resultsFile;
    VanillaInstaller installer;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        resultsFile = tempDir.resolve(".install-vanilla.env");

        final SharedFetch sharedFetch = new SharedFetch("test-vanilla-installer", Options.builder().build());
        final MinecraftVersionsApi versionsApi = new MinecraftVersionsApi(sharedFetch)
            .setManifestUrl(URI.create(wm.baseUrl() + "/mc/game/version_manifest_v2.json"));
        installer = new VanillaInstaller(sharedFetch, versionsApi);
    }

    @ParameterizedTest
    @CsvSource({
        "release,26.2",
        "latest,26.2",
        "snapshot,26.3-snapshot-3",
        "26.2,26.2",
        "1.6,1.6",
    })
    void testModernInstall(String inputVersion, String expectedVersion) throws IOException {
        installer.install(inputVersion, tempDir, resultsFile, false);
        VanillaManifest manifest = Manifests.load(tempDir, VanillaManifest.ID, VanillaManifest.class);

        final String jarName = "minecraft_server." + expectedVersion + ".jar";
        assertThat(manifest).isNotNull();
        assertThat(manifest.minecraftVersion).isEqualTo(expectedVersion);
        assertThat(manifest.serverEntry).isEqualTo(jarName);
        assertThat(manifest.getFiles()).containsExactly(jarName);
        assertThat(tempDir.resolve(jarName)).exists();
        assertResultsFile(expectedVersion, jarName);
    }

    @Test
    void testPre1_6Install() throws IOException {
        installer.install("1.5", tempDir, resultsFile, false);
        VanillaManifest manifest = Manifests.load(tempDir, VanillaManifest.ID, VanillaManifest.class);

        final String jarName = "minecraft_server.1.5.jar";
        final String symlinkName = "minecraft_server.jar";
        final Path symlinkPath = tempDir.resolve(symlinkName);
        final Path jarPath = tempDir.resolve(jarName);
        assertThat(manifest).isNotNull();
        assertThat(manifest.minecraftVersion).isEqualTo("1.5");
        assertThat(manifest.serverEntry).isEqualTo(symlinkName);
        assertThat(manifest.getFiles()).containsExactlyInAnyOrder(jarName, symlinkName);
        assertThat(jarPath).isRegularFile();
        assertThat(symlinkPath).isSymbolicLink();
        final Path symlinkTarget = symlinkPath.getParent().resolve(Files.readSymbolicLink(symlinkPath));

        assertThat(Files.isSameFile(symlinkTarget, jarPath)).isTrue();
        assertResultsFile("1.5", symlinkName);
    }

    @Test
    void testBadChecksumRejected() {
        Assertions.assertThrows(FileHashInvalidException.class, () -> installer.install("bad-sha", tempDir, resultsFile, false));
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void missingServerDownloadRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> installer.install("b1.7.3", tempDir, resultsFile, false));
        assertThat(tempDir).isEmptyDirectory();
    }

    void assertResultsFile(String version, String jarName) throws IOException {
        assertThat(resultsFile).exists();
        final HashMap<String, String> items = new HashMap<>();
        for (String line : Files.readAllLines(resultsFile)) {
            final String[] chunks = line.split("=", 2);
            assertThat(items).doesNotContainKey(chunks[0]);
            items.put(chunks[0], chunks[1]);
        }

        assertThat(items).containsOnly(
            entry("TYPE", "\"VANILLA\""),
            entry("SERVER", '"' + tempDir.resolve(jarName).toString() + '"'),
            entry("VERSION", '"' + version + '"')
        );
    }
}
