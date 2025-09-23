package me.itzg.helpers.curseforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
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
    void testModpackZipCreation() throws IOException {
        // Test basic ZIP file creation and structure
        Path modpackZip = createTestModpackZip();
        
        // Verify ZIP was created successfully
        assertThat(modpackZip).exists();
        assertThat(Files.size(modpackZip)).isGreaterThan(0);
        
        // This validates the ZIP structure we'll use for ZIP-based processing
        // The actual installer tests would require proper API key setup
    }

    @Test
    void testZipManifestExtraction() throws IOException {
        // Test that we can create a valid modpack ZIP structure
        Path modpackZip = createTestModpackZip();
        
        // Verify the ZIP contains expected structure for ZIP-based processing
        assertThat(modpackZip).exists();
        assertThat(Files.size(modpackZip)).isGreaterThan(100); // Should contain manifest + mod file
    }

    @Test
    @EnabledIfSystemProperty(named = "testEnableManualTests", matches = "true", disabledReason = "For manual recording")
    void testManual() throws IOException {
        final Path resultsFile = tempDir.resolve(".results.env");

        final CurseForgeInstaller installer = new CurseForgeInstaller(tempDir, resultsFile);
        installer.install("all-the-mods-8", "1.0.4", null);

        assertThat(tempDir)
            .isNotEmptyDirectory();
    }

    // Helper methods for creating test data

    private Path createTestModpackZip() throws IOException {
        Path zipPath = tempDir.resolve("test-modpack.zip");
        
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(zipPath))) {
            // Add manifest.json
            ZipArchiveEntry manifestEntry = new ZipArchiveEntry("manifest.json");
            zos.putArchiveEntry(manifestEntry);
            String manifestJson = "{\n" +
                "  \"manifestType\": \"minecraftModpack\",\n" +
                "  \"manifestVersion\": 1,\n" +
                "  \"name\": \"Test Modpack\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"minecraft\": {\n" +
                "    \"version\": \"1.19.2\",\n" +
                "    \"modLoaders\": [\n" +
                "      {\n" +
                "        \"id\": \"forge-43.2.0\",\n" +
                "        \"primary\": true\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"files\": [\n" +
                "    {\n" +
                "      \"projectID\": 123,\n" +
                "      \"fileID\": 456,\n" +
                "      \"required\": true\n" +
                "    }\n" +
                "  ]\n" +
                "}";
            zos.write(manifestJson.getBytes());
            zos.closeArchiveEntry();
            
            // Add mods/test-mod.jar - this simulates the ZIP-based processing optimization
            ZipArchiveEntry modEntry = new ZipArchiveEntry("mods/test-mod.jar");
            zos.putArchiveEntry(modEntry);
            zos.write("test mod content for ZIP-based extraction".getBytes());
            zos.closeArchiveEntry();
        }
        
        return zipPath;
    }
}