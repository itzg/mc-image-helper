package me.itzg.helpers.packwiz;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;
import java.util.Map;

@SuperBuilder
@Getter
@Jacksonized
public final class PackwizModpackManifest extends BaseManifest
{
    public static final String ID = "packwiz-modpack";

    private String name;
    private String author;
    private String version;
    // string -> string because the versions can have arbitrary entries
    private Map<String, String> dependencies;

    public String neoforgeVersion() {
        return dependencies.get("neoforge");
    }

    public String forgeVersion() {
        return dependencies.get("forge");
    }

    public String fabricVersion() {
        return dependencies.get("fabric");
    }

    public String quiltVersion() {
        return dependencies.get("quilt");
    }

    public String minecraftVersion() {
        return dependencies.get("minecraft");
    }
}
