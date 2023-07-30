package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ModLoaderType {
    Any, // zero-indexed for this one
    Forge,
    Cauldron,
    LiteLoader,
    Fabric,
    Quilt,
    // undocumented as of 2023-07-23 but referenced in https://www.curseforge.com/minecraft/mc-mods/chimes/files/4671986
    NeoForge;

    @JsonValue
    public int toValue() {
        // zero-indexed for this one
        return ordinal();
    }
}
