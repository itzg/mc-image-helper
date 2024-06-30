package me.itzg.helpers.modrinth;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * Similar to {@link me.itzg.helpers.curseforge.ExcludeIncludesContent}, but trimmed down to match
 * supported functionality
 */
@JsonSchemaTitle("Mods Exclude File Content")
@Data
public class ExcludeIncludesContent {

    @JsonPropertyDescription("Mods/files by slug|id to exclude for all modpacks")
    private Set<String> globalExcludes;
    @JsonPropertyDescription("Mods by slug|id to force include for all modpacks")
    private Set<String> globalForceIncludes;

    @JsonPropertyDescription("Specific exclude/includes by modpack slug")
    private Map<String, me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes> modpacks;

    @Data
    public static class ExcludeIncludes {
        @JsonPropertyDescription("Mods by slug|id to exclude for this modpack")
        private Set<String> excludes;
        @JsonPropertyDescription("Mods by slug|id to force include for this modpack")
        private Set<String> forceIncludes;
    }

}
