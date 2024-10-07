package me.itzg.helpers.modrinth;

import lombok.Getter;

@Getter
public enum Loader {
    fabric("mods", null),
    quilt("mods", fabric),
    forge("mods", null),
    neoforge("mods", forge),
    bukkit("plugins", null),
    spigot("plugins", null),
    paper("plugins", spigot),
    pufferfish("plugins", paper),
    purpur("plugins", paper),
    bungeecord("plugins", null),
    velocity("plugins", null),
    datapack(null, null);

    private final String type;
    private final Loader compatibleWith;

    Loader(String type, Loader compatibleWith) {
        this.type = type;
        this.compatibleWith = compatibleWith;
    }

}
