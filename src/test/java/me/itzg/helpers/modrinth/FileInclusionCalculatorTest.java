package me.itzg.helpers.modrinth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes;
import me.itzg.helpers.modrinth.model.Env;
import me.itzg.helpers.modrinth.model.EnvType;
import me.itzg.helpers.modrinth.model.ModpackIndex.ModpackFile;
import org.junit.jupiter.api.Test;

class FileInclusionCalculatorTest {

    @Test
    void forceIncludeWhenExcludedServerFile() {
        final ExcludeIncludesContent globalContent = new ExcludeIncludesContent();
        globalContent.setGlobalExcludes(Collections.singleton("cloth-config"));

        final FileInclusionCalculator calculator = new FileInclusionCalculator("modpack",
            null,
            Collections.singletonList("cloth"),
            globalContent
        );

        final ModpackFile modFile = new ModpackFile()
        .setEnv(forServerAndClient())
        .setPath("mods/cloth-config-15.0.140-fabric.jar");

        final boolean result = calculator.includeModFile(modFile);
        assertThat(result).isTrue();
    }

    @Test
    void excludeForModpack() {
        final ExcludeIncludesContent globalContent = new ExcludeIncludesContent();
        globalContent.setGlobalExcludes(Collections.singleton("other"));
        final Map<String, ExcludeIncludes> modpacksGlobal = new HashMap<>();
        modpacksGlobal.put("modpackWithExcludes", new ExcludeIncludes().setExcludes(Collections.singleton("cloth-config")));
        globalContent.setModpacks(modpacksGlobal);

        final ModpackFile modFile = new ModpackFile()
            .setEnv(forServerAndClient())
            .setPath("mods/cloth-config-15.0.140-fabric.jar");

        {
            final FileInclusionCalculator calculator = new FileInclusionCalculator("modpack",
                null,
                null,
                globalContent
            );

            assertThat(calculator.includeModFile(modFile)).isTrue();
        }
        {
            final FileInclusionCalculator calculator = new FileInclusionCalculator("modpackWithExcludes",
                null,
                null,
                globalContent
            );

            assertThat(calculator.includeModFile(modFile)).isFalse();
        }
    }

    private Map<Env, EnvType> forServerAndClient() {
        final Map<Env, EnvType> modEnvs = new HashMap<>();
        modEnvs.put(Env.client, EnvType.required);
        modEnvs.put(Env.server, EnvType.required);
        return modEnvs;
    }
}