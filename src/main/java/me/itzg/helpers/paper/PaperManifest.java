package me.itzg.helpers.paper;

import java.net.URI;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class PaperManifest extends BaseManifest {

    public static final String ID = "papermc";

    String minecraftVersion;

    String project;

    int build;

    URI customDownloadUrl;
}
