package me.itzg.helpers.patch.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.Data;

@Data
public class PatchDefinition {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Path to the file to patch")
    String file;

    @JsonPropertyDescription("If non-null, declares a specifically supported format name: json, yaml. " +
            "Otherwise, the file format is detected by the file's suffix.")
    @JsonProperty("file-format")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String fileFormat;

    @JsonProperty(required = true)
    List<PatchOperation> ops;
}
