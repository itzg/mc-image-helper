package me.itzg.helpers.patch.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PatchSetOperation extends PatchOperation {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The JSON path to the field to set.")
    String path;

    @JsonProperty(required = true)
    @JsonPropertyDescription(VALUE_DESCRIPTION)
    JsonNode value;

    @JsonProperty("value-type")
    @JsonPropertyDescription(VALUE_TYPE_DESCRIPTION)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String valueType;
}
