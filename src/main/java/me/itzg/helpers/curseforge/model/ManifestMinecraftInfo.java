package me.itzg.helpers.curseforge.model;

import java.util.List;
import lombok.Data;

@Data
public class ManifestMinecraftInfo {
    private String version;
    private List<ModLoader> modLoaders;
}