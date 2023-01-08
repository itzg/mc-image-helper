package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class Category {
    private int gameId;
    private boolean isClass;
    private int classId;
    private String name;
    private String dateModified;
    private int parentCategoryId;
    private int id;
    private String iconUrl;
    private String slug;
    private String url;
    private int displayIndex;
}