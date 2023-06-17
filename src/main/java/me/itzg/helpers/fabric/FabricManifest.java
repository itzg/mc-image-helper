package me.itzg.helpers.fabric;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class FabricManifest extends BaseManifest {

    public static final String MANIFEST_ID = "fabric";
    /**
     * The path to the launcher. This should also be in {@link #getFiles()}, but provides a specific reference.
     */
    String launcherPath;

    /**
     * Captures how and specifics about current fabric installation.
     */
    Origin origin;

}
