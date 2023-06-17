package me.itzg.helpers.modrinth;

import java.util.Map;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;
import me.itzg.helpers.modrinth.model.DependencyId;

@SuperBuilder
@Getter
@Jacksonized
public class ModrinthModpackManifest extends BaseManifest {
    public static final String ID = "modrinth-modpack";

    private String projectSlug;
    private String versionId;

    private Map<DependencyId, String> dependencies;

}
