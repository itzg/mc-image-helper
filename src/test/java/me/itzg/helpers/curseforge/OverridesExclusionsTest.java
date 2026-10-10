package me.itzg.helpers.curseforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import me.itzg.helpers.json.ObjectMappers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverridesExclusionsTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsPerPackOverrideExclusions() throws Exception {
        final ExcludeIncludesContent content = ObjectMappers.defaultMapper().readValue("""
            {"modpacks":{"example-pack":{
              "excludes":["client-mod"],"forceIncludes":["server-library"],
              "overridesExclusions":["mods/client-*.jar"]
            }}}
            """, ExcludeIncludesContent.class);

        assertThat(content.getModpacks().get("example-pack").getOverridesExclusions())
            .containsExactly("mods/client-*.jar");
        assertThat(content.getModpacks().get("example-pack").getExcludes()).containsExactly("client-mod");
        assertThat(content.getModpacks().get("example-pack").getForceIncludes()).containsExactly("server-library");
    }

    @Test
    void combinesPackAndCommandLinePatternsDuringExtraction() throws Exception {
        final CurseForgeInstaller installer = installerWithPackPatterns();
        installer.setOverridesExclusions(List.of("config/client/**"));

        final Path zip = tempDir.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String path : List.of("mods/client-1.jar", "mods/server-1.jar", "config/client/menu.json", "config/server.cfg")) {
                out.putNextEntry(new ZipEntry("overrides/" + path));
                out.write(path.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }

        final Path output = tempDir.resolve("output");
        final OverridesApplier.Result result = new OverridesFromZipApplier(
            output, zip, false, "overrides", null,
            installer.resolveOverridesExclusions("example-pack")
        ).apply();

        assertThat(output.resolve("mods/client-1.jar")).doesNotExist();
        assertThat(output.resolve("config/client/menu.json")).doesNotExist();
        assertThat(output.resolve("mods/server-1.jar")).hasContent("mods/server-1.jar");
        assertThat(output.resolve("config/server.cfg")).hasContent("config/server.cfg");
        assertThat(result.paths).containsExactlyInAnyOrder(
            output.resolve("mods/server-1.jar"), output.resolve("config/server.cfg")
        );
    }

    @Test
    void doesNotApplyPatternsToAnotherPack() throws Exception {
        final CurseForgeInstaller installer = installerWithPackPatterns();
        installer.setOverridesExclusions(List.of("config/client/**"));

        assertThat(installer.resolveOverridesExclusions("other-pack"))
            .containsExactly("config/client/**");
    }

    @Test
    void preservesCommandLinePatternsWithLegacyConfiguration() throws Exception {
        final CurseForgeInstaller installer = new CurseForgeInstaller(tempDir, tempDir.resolve("results.env"));
        installer.setExcludeIncludes(ObjectMappers.defaultMapper().readValue("""
            {"modpacks":{"example-pack":{"excludes":["client-mod"]}}}
            """, ExcludeIncludesContent.class));
        installer.setOverridesExclusions(List.of("mods/client-*.jar"));

        assertThat(installer.resolveOverridesExclusions("example-pack"))
            .containsExactly("mods/client-*.jar");
    }

    @Test
    void acceptsAbsentConfiguration() {
        final CurseForgeInstaller installer = new CurseForgeInstaller(tempDir, tempDir.resolve("results.env"));

        assertThat(installer.resolveOverridesExclusions("example-pack")).isEmpty();
        installer.setExcludeIncludes(new ExcludeIncludesContent());
        assertThat(installer.resolveOverridesExclusions("example-pack")).isEmpty();
    }

    private CurseForgeInstaller installerWithPackPatterns() throws Exception {
        final CurseForgeInstaller installer = new CurseForgeInstaller(tempDir, tempDir.resolve("results.env"));
        installer.setExcludeIncludes(ObjectMappers.defaultMapper().readValue("""
            {"modpacks":{"example-pack":{"overridesExclusions":["mods/client-*.jar"]}}}
            """, ExcludeIncludesContent.class));
        return installer;
    }
}
