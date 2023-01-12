package me.itzg.helpers.curseforge.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class ManifestFileRef {
    private int projectID;
    private int fileID;
    @EqualsAndHashCode.Exclude
    private boolean required;
}