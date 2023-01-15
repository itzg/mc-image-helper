package me.itzg.helpers.forge;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class ForgeManifest extends BaseManifest {
    String minecraftVersion;
    String forgeVersion;
    /**
     * absolute path to jar file or run script
     */
    String serverEntry;
}
