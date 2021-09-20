package me.itzg.helpers.patch.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PatchPutOperation extends PatchOperation {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The JSON path to the object containing key to set")
    String path;

    @JsonProperty(required = true)
    @JsonPropertyDescription("The key to set")
    String key;

    /**
     * The value to set, which may contain embedded variable placeholders with the syntax ${...}
     */
    @JsonProperty(required = true)
    @JsonPropertyDescription(VALUE_DESCRIPTION)
    JsonNode value;

    @JsonPropertyDescription(VALUE_TYPE_DESCRIPTION)
    @JsonProperty("value-type")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String valueType;
}
