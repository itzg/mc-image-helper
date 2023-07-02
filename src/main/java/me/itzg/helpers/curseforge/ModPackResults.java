package me.itzg.helpers.curseforge;

import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class ModPackResults {
    private String name;
    private String version;
    private List<Path> files;
    private String minecraftVersion;
    private String modLoaderId;
    private String levelName;
    private List<PathWithInfo> needsDownload;
}
