package me.itzg.helpers.purpur;

import java.net.URI;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class PurpurManifest extends BaseManifest {

    public static final String ID = "purpur";

    String minecraftVersion;

    String build;

    URI customDownloadUrl;
}
