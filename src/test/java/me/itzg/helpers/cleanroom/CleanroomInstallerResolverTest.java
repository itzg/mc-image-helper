package me.itzg.helpers.cleanroom;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import me.itzg.helpers.files.Manifests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

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

}
