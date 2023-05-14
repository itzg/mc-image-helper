package me.itzg.helpers.modrinth;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@SuperBuilder
@Getter
@Jacksonized
public class ModrinthModpackManifest extends BaseManifest {
    public static final String ID = "modrinth-modpack";

    private String projectSlug;
    private String versionId;


}
