package me.itzg.helpers.fabric;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;
import me.itzg.helpers.fabric.FabricManifest.Versions;
import me.itzg.helpers.files.Manifests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricLauncherInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveManifest() {
        final FabricManifest manifest = FabricManifest.builder()
            .origin(
                Versions.builder()
                .gameVersion("1.19.2")
                .loaderVersion("0.14.11")
                .installerVersion("0.11.1")
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
}