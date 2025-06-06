package me.itzg.helpers.fabric;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
class FabricLauncherInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveManifest() {
        final FabricManifest manifest = FabricManifest.builder()
            .origin(
                Versions.builder()
                .game("1.19.2")
                .loader("0.14.11")
                .build()
            )
            .files(Collections.singletonList("fabric-server-mc.1.19.2-loader.0.14.11-launcher.0.11.1.jar"))
            .build();

        final Path manifestPath = Manifests.save(tempDir, "fabric", manifest);
        assertThat(manifestPath.toString())
            .contains("fabric");

        final FabricManifest loaded = Manifests.load(tempDir, "fabric", FabricManifest.class);

        assertThat(loaded)
            .isEqualTo(manifest);
    }

    @Test
    void testInstallUsingVersions_onlyGameVersion(WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wm = wmRuntimeInfo.getWireMock();
        wm.loadMappingsFrom("src/test/resources/fabric");

        final Path resultsFile = tempDir.resolve("results.env");
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(
            tempDir
        )
            .setResultsFile(resultsFile);
        installer.setFabricMetaBaseUrl(wmRuntimeInfo.getHttpBaseUrl());

        installer.installUsingVersions(buildSharedFetchOptions(), "1.19.3", null, null);

        final Path expectedLauncherPath = tempDir.resolve("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar");
        assertThat(expectedLauncherPath)
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1");

        assertThat(resultsFile)
            .exists()
            .hasContent("SERVER=\"" + expectedLauncherPath + "\"" +
                "\nFAMILY=\"FABRIC\"" +
                "\nTYPE=\"FABRIC\"" +
                "\nVERSION=\"1.19.3\""
            );

        final Path expectedManifestFile = tempDir.resolve(".fabric-manifest.json");
        assertThat(expectedManifestFile)
            .exists();

        assertJson(expectedManifestFile.toFile())
            .at("/launcherPath").hasValue(expectedLauncherPath.toString())
            .at("/origin/game").hasValue("1.19.3")
            .at("/origin/loader").hasValue("0.14.12")
            .at("/files").isArrayContaining("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar");
    }

    private Options buildSharedFetchOptions() {
        return Options.builder().build();
    }

    @Test
    void testWithProvidedUri(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(
            head(urlEqualTo("/fabric-launcher.jar"))
                .willReturn(aResponse().withStatus(200))
        );
        stubFor(
            get(urlEqualTo("/fabric-launcher.jar"))
                .willReturn(aResponse()
                    .withStatus(200)
                    // can't use withBodyFile
                    .withBodyFile("fabric-empty-launcher.jar")
                )
        );

        final Path expectedResultsPath = tempDir.resolve("results.env");
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir)
            .setResultsFile(expectedResultsPath);
        final URI loaderUri = URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/fabric-launcher.jar");

        installer.installUsingUri(Options.builder().build(), loaderUri);

        final Path expectedLauncherPath = tempDir.resolve("fabric-launcher.jar");
        assertThat(expectedLauncherPath)
            .exists();

        assertThat(expectedResultsPath)
            .exists()
            .hasContent("SERVER=\"" + expectedLauncherPath + "\"" +
                "\nFAMILY=\"FABRIC\"" +
                "\nTYPE=\"FABRIC\"" +
                "\nVERSION=\"1.19.4\"");

    }

    @Test
    void testWithProvidedUri_contentDisposition(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(
            head(urlEqualTo("/server"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_DISPOSITION, "attachment; filename=\"fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar\"")
                )
        );
        stubFor(
            get(urlEqualTo("/server"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_DISPOSITION, "attachment; filename=\"fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar\"")
                    .withBody("testWithProvidedUri_contentDisposition")
                )
        );

        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir);
        installer.installUsingUri(
            Options.builder().build(), URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/server")
        );

        final Path expectedLauncherPath = tempDir.resolve("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar");
        assertThat(expectedLauncherPath)
            .exists()
            .hasContent("testWithProvidedUri_contentDisposition");
    }

    @Test
    void testWithLocalLauncherFile() throws IOException {
        final Path expectedResultsPath = tempDir.resolve("results.env");
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir)
            .setResultsFile(expectedResultsPath);

        final Path launcherFile = Paths.get("src/test/resources/__files/fabric-empty-launcher.jar");
        installer.installUsingLocalFile(
            launcherFile
        );

        assertThat(expectedResultsPath)
            .exists()
            .hasContent("SERVER=\"" + launcherFile + "\"" +
                "\nFAMILY=\"FABRIC\"" +
                "\nTYPE=\"FABRIC\"" +
                "\nVERSION=\"1.19.4\"");
    }

    @Test
    void testUpgradeFromVersionToVersion(WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wm = wmRuntimeInfo.getWireMock();
        wm.loadMappingsFrom("src/test/resources/fabric");

        final FabricLauncherInstaller installer = new FabricLauncherInstaller(
            tempDir
        );
        installer.setFabricMetaBaseUrl(wmRuntimeInfo.getHttpBaseUrl());

        installer.installUsingVersions(
            buildSharedFetchOptions(), "1.19.2", null, null
        );

        final Path expectedLauncher192 = tempDir.resolve("fabric-server-mc.1.19.2-loader.0.14.12-launcher.0.11.1.jar");
        assertThat(expectedLauncher192)
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.2-loader.0.14.12-launcher.0.11.1");

        // Now upgrade from 1.19.2 to 1.19.3

        installer.installUsingVersions(
            buildSharedFetchOptions(), "1.19.3", null, null
        );

        final Path expectedLauncher193 = tempDir.resolve("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar");
        assertThat(expectedLauncher193)
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1");

        assertThat(expectedLauncher192)
            .doesNotExist();
    }

    @Test
    void testNoNetworkUsageWhenVersionMatches(WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wm = wmRuntimeInfo.getWireMock();
        wm.loadMappingsFrom("src/test/resources/fabric");

        final FabricLauncherInstaller installer = new FabricLauncherInstaller(
            tempDir
        );
        installer.setFabricMetaBaseUrl(wmRuntimeInfo.getHttpBaseUrl());

        installer.installUsingVersions(
            buildSharedFetchOptions(), "1.19.2", null, null
        );

        wm.verifyThat(
            // since minecraft version is pinned
            0,
            getRequestedFor(urlEqualTo("/v2/versions/game")));
        wm.verifyThat(
            // to lookup installer version
            1,
            getRequestedFor(urlEqualTo("/v2/versions/installer")));

        final Path expectedLauncher192 = tempDir.resolve("fabric-server-mc.1.19.2-loader.0.14.12-launcher.0.11.1.jar");
        assertThat(expectedLauncher192)
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.2-loader.0.14.12-launcher.0.11.1");

        // Now try again with same

        wm.resetRequests();

        installer.installUsingVersions(
            buildSharedFetchOptions(), "1.19.2", "0.14.12", "0.11.1"
        );

        assertThat(expectedLauncher192)
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.2-loader.0.14.12-launcher.0.11.1");

        wm.verifyThat(0, getRequestedFor(urlEqualTo("/v2/versions/game")));
        wm.verifyThat(0, getRequestedFor(urlEqualTo("/v2/versions/installer")));
    }

    @Test
    @EnabledIfSystemProperty(named = "testEnableManualTests", matches = "true", disabledReason = "For manual recording")
    void forRecordingVersionDiscovery() {
        final Path resultsFile = tempDir.resolve("results.env");
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir)
            .setResultsFile(resultsFile);
        installer.setFabricMetaBaseUrl("http://localhost:8080");

        installer.installUsingVersions(buildSharedFetchOptions(), "1.19.3", null, null);
    }
}