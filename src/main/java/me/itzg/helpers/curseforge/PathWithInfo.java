package me.itzg.helpers.curseforge;

import java.nio.file.Path;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter @Setter
public class PathWithInfo {
    private final Path path;
    /**
     * If this is a world mod file, then this will be the level name that would reference it
     */
    private String levelName;
}
