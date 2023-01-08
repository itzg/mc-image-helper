package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FileReleaseType {
    release,
    beta,
    alpha;

    @JsonValue
    public int toValue() {
        return this.ordinal()+1;
    }
}
