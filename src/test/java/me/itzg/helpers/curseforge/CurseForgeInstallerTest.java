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

    /*
Scenarios to test

###
Duplicated slug for modpack and mod (hyperion)

###
Exclude/include by "gameVersions"

    "gameVersions": [
      "Client",
      "1.16.5",
      "Forge"
    ],

Reject
### Oculus mc1.16.5-1.4.5
GET https://api.curse.tools/v1/cf/mods/581495/files/4300427


Keep
    ### [Fabric] Resourceful Lib 1.2.2
GET https://api.curse.tools/v1/cf/mods/570073/files/4326308
        "gameVersions": [
      "1.19.3",
      "Fabric",
      "Client",
      "Server"
    ],

  ### Patchouli-1.19.2-77.jar
GET https://api.curse.tools/v1/cf/mods/306770/files/4031402
  "gameVersions": [
      "1.19.2",
      "Forge"
    ],

     */

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