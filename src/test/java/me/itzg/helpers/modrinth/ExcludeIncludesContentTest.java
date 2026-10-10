package me.itzg.helpers.modrinth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import java.util.Map;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.Env;
import me.itzg.helpers.modrinth.model.EnvType;
import me.itzg.helpers.modrinth.model.ModpackIndex.ModpackFile;
import org.junit.jupiter.api.Test;

class ExcludeIncludesContentTest {
    @Test
    void schemaOnlyAdvertisesSupportedPackFields() {
        final JsonSchemaGenerator generator = new JsonSchemaGenerator(ObjectMappers.defaultMapper());
        final JsonNode properties = generator.generateJsonSchema(ExcludeIncludesContent.class)
            .path("definitions").path("ExcludeIncludes").path("properties");

        assertThat(properties.has("excludes")).isTrue();
        assertThat(properties.has("forceIncludes")).isTrue();
        assertThat(properties.has("overridesExclusions")).isFalse();
        assertThat(generator.generateJsonSchema(me.itzg.helpers.curseforge.ExcludeIncludesContent.class)
            .path("definitions").path("ExcludeIncludes").path("properties").has("overridesExclusions"))
            .isTrue();
    }

    @Test
    void retainsPackExclusionsAndForceIncludesFromJson() throws Exception {
        final ExcludeIncludesContent content = ObjectMappers.defaultMapper().readValue("""
            {"globalExcludes":["library"],"modpacks":{"example-pack":{
              "excludes":["client-mod"],"forceIncludes":["library"]
            }}}
            """, ExcludeIncludesContent.class);
        final FileInclusionCalculator calculator = new FileInclusionCalculator("example-pack", null, null, content);
        final Map<Env, EnvType> env = Map.of(Env.client, EnvType.required, Env.server, EnvType.required);

        assertThat(calculator.includeModFile(new ModpackFile().setEnv(env).setPath("mods/client-mod.jar"))).isFalse();
        assertThat(calculator.includeModFile(new ModpackFile().setEnv(env).setPath("mods/library.jar"))).isTrue();
        assertThat(calculator.includeModFile(new ModpackFile().setEnv(env).setPath("mods/server-mod.jar"))).isTrue();
        assertThat(new FileInclusionCalculator("other-pack", null, null, content)
            .includeModFile(new ModpackFile().setEnv(env).setPath("mods/library.jar"))).isFalse();
    }
}
