package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DependencyId {
    minecraft,
    forge,
    @JsonProperty("fabric-loader")
    fabricLoader,
    @JsonProperty("quilt-loader")
    quiltLoader,
    neoforge
}
