package me.itzg.helpers.patch.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PatchSetOperation.class, name = "$set"),
        @JsonSubTypes.Type(value = PatchPutOperation.class, name = "$put")
})
public abstract class PatchOperation {
    protected static final String VALUE_DESCRIPTION = "The value to set." +
            " If the given value is a string, variable placeholders of the form ${...} will be replaced" +
            " and the resulting string can be converted by setting value-type.";
    protected static final String VALUE_TYPE_DESCRIPTION = "Used to convert string values into other value types:" +
            " int, float, bool, auto," +
            " list of int, list of float, list of bool, list of auto";
}
