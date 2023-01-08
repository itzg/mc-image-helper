package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class ManifestFileRef {
    private int projectID;
    private boolean required;
    private int fileID;
}