package me.itzg.helpers.versions;

import java.net.URI;
import lombok.Data;

@Data
public class MinecraftVersionMetadata {

    @Data
    public static class Downloads {
        private Download server;
    }

    @Data
    public static class Download {
        private URI url;
        private String sha1;
    }

    private Downloads downloads;
}