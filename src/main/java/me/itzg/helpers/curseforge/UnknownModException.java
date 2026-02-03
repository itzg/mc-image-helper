package me.itzg.helpers.curseforge;

import lombok.Getter;

@Getter
public class UnknownModException extends RuntimeException {

    private final String slug;

    public UnknownModException(String slug) {
        super("Unknown mod '" + slug + "':");
        this.slug = slug;
    }
}
