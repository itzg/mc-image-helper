package me.itzg.helpers.mvn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.List;
import lombok.Data;

@Data
public class MavenMetadata {

    String groupId;
    String artifactId;
    Versioning versioning;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Versioning {
        String latest;
        String release;
        @JacksonXmlElementWrapper(localName = "versions")
        List<String> version;
    }
}
