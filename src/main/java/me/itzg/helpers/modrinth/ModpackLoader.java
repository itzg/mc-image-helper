package me.itzg.helpers.modrinth;

/**
 * Valid loader values for modpacks
 */
public enum ModpackLoader {
    fabric,
    forge,
    quilt;

    public Loader asLoader() {
        return Loader.valueOf(this.name());
    }
}
