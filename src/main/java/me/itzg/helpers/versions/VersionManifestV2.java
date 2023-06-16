package me.itzg.helpers.versions;

import java.net.URI;
import java.util.List;
import lombok.Data;

@Data
public class VersionManifestV2 {
    public enum VersionType {
        release,
        snapshot,
        old_beta,
        old_alpha
    }

    @Data
    public static class Latest {
        private String release;
        private String snapshot;
    }

    @Data
    public static class Version {
        private String id;
        private VersionType type;
        private URI url;
    }

    private Latest latest;

    private List<Version> versions;
}
