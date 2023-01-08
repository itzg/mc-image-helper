package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class ModAsset {
    private String description;
    private int id;
    private String title;
    private int modId;
    private String url;
    private String thumbnailUrl;
}