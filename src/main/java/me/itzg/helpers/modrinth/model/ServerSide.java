package me.itzg.helpers.modrinth.model;

public enum ServerSide {
    required,
    optional,
    unsupported,
    /**
     * Not a documented value, but dynmap project was responding with this.
     */
    unknown
}
