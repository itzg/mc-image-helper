package me.itzg.helpers.modrinth;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class LegacyModrinthManifest {

    public static final String FILENAME = ".modrinth-files.manifest";

    Instant timestamp;

    Set<String> files;
}
