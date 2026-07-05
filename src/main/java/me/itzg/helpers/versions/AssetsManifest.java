package me.itzg.helpers.versions;

import lombok.Data;
import java.net.URI;

@Data
public class AssetsManifest {
    @Data
    public static class Downloads {
        private JarInfo server;
    }

    @Data
    public static class JarInfo {
        private URI url;
    }

    private Downloads downloads;
}
