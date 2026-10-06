package me.itzg.helpers.cleanroom;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;

@WireMockTest
public class CleanroomInstallerResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveManifest() {
        final String installerVersion = "0.1.4";
        final String loaderVersion = "0.6.13-alpha";

        final CleanroomManifest manifest = CleanroomManifest.builder()
            .installerVersion(installerVersion)
            .loaderVersion(loaderVersion)
            .files(Collections.singletonList("cleanroom-" + loaderVersion +".jar"))
            .build();

        Manifests.save(tempDir, "cleanroom", manifest);
        assertThat(tempDir.resolve(".cleanroom-manifest.json")).exists();

        final CleanroomManifest loaded = Manifests.load(tempDir, "cleanroom", CleanroomManifest.class);

        assertThat(loaded)
            .isEqualTo(manifest);
    }

    @Test
    void testInstallUsingVersions_onlyLoaderVersion(WireMockRuntimeInfo wmRuntimeInfo) {
        final WireMock wm = wmRuntimeInfo.getWireMock();
//        wm.loadMappingsFrom("src/test/resources/cleanroom");

        final Path resultsFile = tempDir.resolve("results.env");
        final CleanroomInstaller installer = new CleanroomInstaller()
            .outputDirectory(tempDir)
            .resultsFile(resultsFile)
            .mavenUrl(wmRuntimeInfo.getHttpBaseUrl())
            .sharedFetchOptions(buildSharedFetchOptions())
            .loaderVersion("0.6.13-alpha")
            .dryRun();

        stubFor(get("/com/cleanroommc/installer/maven-metadata.xml")
            .willReturn(aResponse()
                .withBodyFile("cleanroom/com-cleanroom-installer-maven-metadata.xml")
            )
        );

        stubFor(
            head(urlEqualTo("/com/cleanroommc/installer/0.1.4/installer-0.1.4.jar"))
                .willReturn(aResponse().withStatus(200))
        );
        stubFor(
            get(urlEqualTo("/com/cleanroommc/installer/0.1.4/installer-0.1.4.jar"))
                .willReturn(aResponse()
                    .withStatus(200)
                    // can't use withBodyFile
                    .withBodyFile("cleanroom/installer-0.1.4.jar")
                )
        );

        assertThat(installer.install()).isEqualTo(true);

        assertThat(resultsFile)
            .exists()
            .hasContent("SERVER=\"run.sh\"" +
                "\nFAMILY=\"FORGE\"" +
                "\nTYPE=\"CLEANROOM\"" +
                "\nVERSION=\"0.6.13-alpha\""
            );

        final Path expectedManifestFile = tempDir.resolve(".cleanroom-manifest.json");
        assertThat(expectedManifestFile)
            .exists();

        assertJson(expectedManifestFile.toFile())
            .at("/serverEntry").hasValue("run.sh")
            .at("/installerVersion").size().isGreaterThan(0)
            .at("/loaderVersion").hasValue("0.6.13-alpha");
    }

    private Options buildSharedFetchOptions() {
        return Options.builder().build();
    }

}
