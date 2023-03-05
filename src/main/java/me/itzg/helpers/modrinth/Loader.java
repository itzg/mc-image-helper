package me.itzg.helpers.modrinth;

public enum Loader {
  fabric("mods"),
  forge("mods"),
  bukkit("plugins"),
  spigot("plugins"),
  paper("plugins"),
  purpur("plugins");

  private final String type;

  Loader(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
