package me.itzg.helpers.vanilla;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class VanillaManifest extends BaseManifest {
    public static final String ID = "vanilla";

    String minecraftVersion;
    String serverEntry;
}
