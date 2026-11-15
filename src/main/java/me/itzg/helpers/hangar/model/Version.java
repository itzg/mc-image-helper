package me.itzg.helpers.hangar.model;

import java.util.Map;
import lombok.Data;

@Data
public class Version {
    String name;
    /**
     * Keyed by {@link Platform} name
     */
    Map<String, PlatformVersionDownload> downloads;
}
