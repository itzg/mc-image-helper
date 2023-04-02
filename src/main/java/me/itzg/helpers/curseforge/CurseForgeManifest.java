package me.itzg.helpers.curseforge;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class CurseForgeManifest extends BaseManifest {

    private String modpackName;
    private String modpackVersion;

    private String slug;
    private int modId;
    private int fileId;
    private String fileName;

    private String minecraftVersion;
    private String modLoaderId;
    private String levelName;
}
