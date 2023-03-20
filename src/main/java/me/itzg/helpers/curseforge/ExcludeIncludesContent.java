package me.itzg.helpers.curseforge;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@JsonSchemaTitle("Mods Exclude/Include File Content")
@Data
public class ExcludeIncludesContent {

    @JsonPropertyDescription("Mods by slug|id to exclude for all modpacks")
    private Set<String> globalExcludes;
    @JsonPropertyDescription("Mods by slug|id to force include for all modpacks")
    private Set<String> globalForceIncludes;

    @JsonPropertyDescription("Specific exclude/includes by modpack slug")
    private Map<String, ExcludeIncludes> modpacks;

    @Data
    public static class ExcludeIncludes {
        @JsonPropertyDescription("Mods by slug|id to exclude for this modpack")
        private Set<String> excludes;
        @JsonPropertyDescription("Mods by slug|id to force include for this modpack")
        private Set<String> forceIncludes;
    }
}
