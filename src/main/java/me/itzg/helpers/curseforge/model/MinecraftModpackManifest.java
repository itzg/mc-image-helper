package me.itzg.helpers.curseforge.model;

import java.util.List;
import lombok.Data;

@Data
public class MinecraftModpackManifest{
    private ManifestMinecraftInfo minecraft;
    private int manifestVersion;
    private String author;
    private String name;
    private ManifestType manifestType;
    private List<ManifestFileRef> files;
    private String overrides;
    private String version;
}