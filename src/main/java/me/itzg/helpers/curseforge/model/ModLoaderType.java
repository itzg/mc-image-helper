package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ModLoaderType {
    Any, // zero-indexed for this one
    Forge,
    Cauldron,
    LiteLoader,
    Fabric,
    Quilt;

    @JsonValue
    public int toValue() {
        // zero-indexed for this one
        return ordinal();
    }
}
