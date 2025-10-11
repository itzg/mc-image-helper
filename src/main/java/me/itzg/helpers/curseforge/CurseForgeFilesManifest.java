package me.itzg.helpers.curseforge;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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

    @Override
    public Collection<String> getFiles() {
        return entries != null ? entries.stream()
            .map(FileEntry::getFilePath)
            .collect(Collectors.toList())
            : Collections.emptyList();
    }
}
