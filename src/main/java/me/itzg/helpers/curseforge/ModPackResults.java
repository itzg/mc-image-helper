package me.itzg.helpers.curseforge;

import java.nio.file.Path;
import java.util.List;
import lombok.Data;

@Data
public class ModPackResults {
    private List<Path> files;
    private String minecraftVersion;
    private String modLoaderId;
    private String levelName;
}
