package me.itzg.helpers.oci;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OciManifest {

    public static final String MEDIA_TYPE_OCI = "application/vnd.oci.image.manifest.v1+json";
    public static final String MEDIA_TYPE_IMAGE_MANIFEST_V2 =
        "application/vnd.docker.distribution.manifest.v2+json";

    public static final String MEDIA_TYPE_OCI_INDEX = "application/vnd.oci.image.index.v1+json";
    public static final String MEDIA_TYPE_IMAGE_INDEX_V2 =
        "application/vnd.docker.distribution.manifest.list.v2+json";

    private int schemaVersion;
    private String mediaType;
    private String artifactType;
    private List<OciLayer> layers;
    private Map<String, String> annotations;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OciLayer {
        private String mediaType;
        private String digest;
        private long size;
        private Map<String, String> annotations;

        public String title() {
            return annotations != null
                ? annotations.get("org.opencontainers.image.title")
                : null;
        }
    }
}
