package me.itzg.helpers.curseforge;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class CurseForgeFilesManifest extends BaseManifest {

    public static final String ID = "curseforge-files";

    @Data
    @Builder
    @Jacksonized
    public static class FileEntry {
        final ModFileIds ids;
        final String filePath;
    }

    List<FileEntry> entries;
}
