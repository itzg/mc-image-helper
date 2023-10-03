package me.itzg.helpers.forge;

import java.nio.file.Path;

public interface InstallerResolver {

    VersionPair resolve();

    Path download(String minecraftVersion, String forgeVersion, Path outputDir);

    void cleanup(Path forgeInstallerJar);
}
