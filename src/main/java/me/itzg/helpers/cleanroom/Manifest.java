package me.itzg.helpers.cleanroom;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class Manifest extends BaseManifest {
    String installerVersion;

    String cleanroomVersion;
    /**
     * absolute path to jar file or run script
     */
    String serverEntry;
}

