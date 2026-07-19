package me.itzg.helpers.versions;

import lombok.Data;
import java.net.URI;

@Data
public final class MinecraftVersionInfo {
    private final String version;
    private final URI manifestUrl;
}
