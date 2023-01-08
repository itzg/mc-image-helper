package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum HashAlgo {
    Sha1,
    Md5;

    @JsonValue
    public int toValue() {
        return ordinal() + 1;
    }

}
