package me.itzg.helpers.paper.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ReleaseChannel {
    @JsonProperty("default")
    DEFAULT,
    @JsonProperty("experimental")
    EXPERIMENTAL;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
