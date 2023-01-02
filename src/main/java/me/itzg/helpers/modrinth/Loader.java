package me.itzg.helpers.modrinth;

public enum Loader {
  fabric("mods"),
  forge("mods"),
  spigot("plugins");

  private final String type;

  Loader(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
