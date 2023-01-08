package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FileRelationType {
    EmbeddedLibrary,
    OptionalDependency,
    RequiredDependency,
    Tool,
    Incompatible,
    Include;

    @JsonValue
    public int toValue() {
        return ordinal()+1;
    }

}
