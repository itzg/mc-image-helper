package me.itzg.helpers.forge;

import java.nio.file.Path;

public interface InstallerResolver {

    VersionPair resolve(ForgeManifest prevManifest);

    Path download(String minecraftVersion, String forgeVersion, Path outputDir);

    void cleanup(Path forgeInstallerJar);

    String getDescription();

    /**
     * @return installer URL when this resolver downloads one, otherwise null
     */
    default String getInstallerUrl() {
        return null;
    }
}
