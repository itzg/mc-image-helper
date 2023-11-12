package me.itzg.helpers.modrinth;

import java.nio.file.Path;
import lombok.Value;

@Value
public class FetchedPack {
    Path mrPackFile;

    String projectSlug;

    String versionId;

    /**
     * Human-readable version
     */
    String versionNumber;
}
