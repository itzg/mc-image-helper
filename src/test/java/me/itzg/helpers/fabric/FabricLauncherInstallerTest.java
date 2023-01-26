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
                .installer("0.11.1")
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
    void testInstallUsingVersions_onlyGameVersion(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        final WireMock wm = wmRuntimeInfo.getWireMock();
        wm.loadMappingsFrom("src/test/resources/fabric");

        final Path resultsFile = tempDir.resolve("results.env");
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(
            tempDir, resultsFile
        );
        installer.setFabricMetaBaseUrl(wmRuntimeInfo.getHttpBaseUrl());

        final Path launcherPath = installer.installUsingVersions("1.19.3", null, null);

        assertThat(launcherPath)
            .isEqualTo(tempDir.resolve("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar"))
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1");

        assertThat(resultsFile)
            .exists()
            .hasContent("SERVER=\"" + launcherPath + "\"" +
                "\nFAMILY=\"FABRIC\"");

        final Path expectedManifestFile = tempDir.resolve(".fabric-manifest.json");
        assertThat(expectedManifestFile)
            .exists();

        assertJson(expectedManifestFile.toFile())
            .at("/launcherPath").hasValue(launcherPath.toString())
            .at("/origin/game").hasValue("1.19.3")
            .at("/origin/loader").hasValue("0.14.12")
            .at("/origin/installer").hasValue("0.11.1")
            .at("/files").isArrayContaining("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar");
    }

    @Test
    void testWithProvidedFile() throws IOException {
        final Path resultsFile = tempDir.resolve("results.env");
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir, resultsFile);

        final Path givenFile = Paths.get("src/test/resources/fabric/test-file.txt");
        installer.installGivenLauncherFile(givenFile);

        assertThat(resultsFile)
            .exists()
            .hasContent("SERVER=\"" + givenFile + "\""
                +"\nFAMILY=\"FABRIC\""
            );

        final Path expectedManifestFile = tempDir.resolve(".fabric-manifest.json");
        assertThat(expectedManifestFile)
            .exists();

        assertJson(expectedManifestFile.toFile())
            .at("/launcherPath").isText(givenFile.toString())
            .at("/origin/@type").isText("file")
            .at("/origin/checksum").isText("sha256:5c2d133f4e4263ee18630616a53579f561005bbe2777e59f298eaac05be0eaae")
            .at("/files").isNull();
    }

    @Test
    void testWithProvidedUri(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        stubFor(
            head(urlEqualTo("/fabric.jar"))
                .willReturn(aResponse().withStatus(200))
        );
        stubFor(
            get(urlEqualTo("/fabric.jar"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("Just a test")
                )
        );

        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir, null);
        final URI loaderUri = URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/fabric.jar");

        // twice to ensure idempotent
        for (int i = 0; i < 2; i++) {
            installer.installUsingUri(loaderUri);

            assertThat(tempDir.resolve("fabric.jar"))
                .exists()
                .hasContent("Just a test");

        }
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

        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir, null);
        final Path actualLauncherPath = installer.installUsingUri(
            URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/server")
        );

        final Path expectedLauncherPath = tempDir.resolve("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar");
        assertThat(expectedLauncherPath)
            .exists()
            .isEqualTo(actualLauncherPath)
            .hasContent("testWithProvidedUri_contentDisposition");
    }

    @Test
    void testUpgradeFromVersionToVersion(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        final WireMock wm = wmRuntimeInfo.getWireMock();
        wm.loadMappingsFrom("src/test/resources/fabric");

        final FabricLauncherInstaller installer = new FabricLauncherInstaller(
            tempDir, null
        );
        installer.setFabricMetaBaseUrl(wmRuntimeInfo.getHttpBaseUrl());

        final Path launcherPath1192 = installer.installUsingVersions(
            "1.19.2", null, null
        );

        assertThat(launcherPath1192)
            .isEqualTo(tempDir.resolve("fabric-server-mc.1.19.2-loader.0.14.12-launcher.0.11.1.jar"))
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.2-loader.0.14.12-launcher.0.11.1");

        // Now upgrade from 1.19.2 to 1.19.3

        final Path launcherPath1193 = installer.installUsingVersions(
            "1.19.3", null, null
        );

        assertThat(launcherPath1193)
            .isEqualTo(tempDir.resolve("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1.jar"))
            .isNotEmptyFile()
            .hasContent("fabric-server-mc.1.19.3-loader.0.14.12-launcher.0.11.1");

        assertThat(launcherPath1192)
            .doesNotExist();
    }

    @Test
    @EnabledIfSystemProperty(named = "testEnableManualTests", matches = "true", disabledReason = "For manual recording")
    void forRecordingVersionDiscovery() throws IOException {
        final Path resultsFile = tempDir.resolve("results.env");
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(tempDir, resultsFile);
        installer.setFabricMetaBaseUrl("http://localhost:8080");

        final Path installerPath = installer.installUsingVersions("1.19.3", null, null);

        assertThat(installerPath)
            .exists()
            .isNotEmptyFile();
    }
}