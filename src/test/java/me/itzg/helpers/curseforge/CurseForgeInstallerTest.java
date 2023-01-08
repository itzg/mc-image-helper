package me.itzg.helpers.curseforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class CurseForgeInstallerTest {
    @TempDir
    Path tempDir;

    @Test
    @EnabledIfSystemProperty(named = "testEnableManualTests", matches = "true", disabledReason = "For manual recording")
    void testManual() throws IOException {
        final Path resultsFile = tempDir.resolve(".results.env");

        final CurseForgeInstaller installer = new CurseForgeInstaller(tempDir, resultsFile);
        installer.install("all-the-mods-8", "1.0.4", null);

        assertThat(tempDir)
            .isNotEmptyDirectory();
    }
}