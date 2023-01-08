package me.itzg.helpers.curseforge;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class CurseForgeManifest extends BaseManifest {

    int modId;
    int fileId;

}
