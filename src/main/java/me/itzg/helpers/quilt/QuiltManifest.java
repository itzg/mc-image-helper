package me.itzg.helpers.quilt;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class QuiltManifest extends BaseManifest {
    public static final String ID = "quilt";

    String minecraftVersion;

    String loaderVersion;
}
