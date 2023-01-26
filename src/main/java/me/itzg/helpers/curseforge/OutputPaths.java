package me.itzg.helpers.curseforge;

import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
class OutputPaths {
    private final Path modsDir;
    private final Path pluginsDir;
    private final Path worldsDir;
}
