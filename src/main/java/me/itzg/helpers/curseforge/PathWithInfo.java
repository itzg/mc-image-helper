package me.itzg.helpers.curseforge;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;

import java.nio.file.Path;

@RequiredArgsConstructor
@Getter @Setter
public class PathWithInfo {
    private final Path path;
    /**
     * If this is a world mod file, then this will be the level name that would reference it
     */
    private String levelName;

    private boolean downloadNeeded;
    private CurseForgeMod modInfo;
    private CurseForgeFile curseForgeFile;
}
