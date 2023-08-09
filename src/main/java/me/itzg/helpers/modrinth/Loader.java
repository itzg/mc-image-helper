package me.itzg.helpers.modrinth;

public enum Loader {
  fabric("mods"),
  quilt("mods"),
  forge("mods"),
  bukkit("plugins"),
  spigot("plugins"),
  paper("plugins"),
  purpur("plugins"),
  bungeecord("plugins"),
  velocity("plugins");

  private final String type;

  Loader(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
