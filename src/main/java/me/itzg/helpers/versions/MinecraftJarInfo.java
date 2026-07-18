package me.itzg.helpers.versions;

import lombok.Data;
import me.itzg.helpers.files.ChecksumAlgo;
import java.net.URI;

@Data
public final class MinecraftJarInfo {
    private final URI url;
    private final int size;
    private final ChecksumAlgo checksumAlgo;
    private final String checksum;
}
