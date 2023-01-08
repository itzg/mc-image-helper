package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class FileIndex {
    private String filename;
    private FileReleaseType releaseType;
    private String gameVersion;
    private Integer gameVersionTypeId;
    private ModLoaderType modLoader;
    private int fileId;
}