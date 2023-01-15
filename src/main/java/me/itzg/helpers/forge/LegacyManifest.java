package me.itzg.helpers.forge;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class LegacyManifest {

    public static final String FILENAME = ".forge.manifest";

    Instant timestamp;

    String minecraftVersion;
    String forgeVersion;
    /**
     * jar file or run script relative to output location
     */
    String serverEntry;
    Set<String> files;
}
