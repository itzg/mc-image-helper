package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Data;

@Data
@JsonTypeName("Mod")
public class CurseForgeMod {
    private boolean isAvailable;
    private boolean allowModDistribution;
    private List<ModAsset> screenshots;
    private int classId;
    private List<FileIndex> latestFilesIndexes;
    private String dateCreated;
    private ModAsset logo;
    private ModLinks links;
    private String dateReleased;
    private int id;
    private List<Category> categories;
    private boolean isFeatured;
    private String slug;
    private int gameId;
    private String summary;
    private List<CurseForgeFile> latestFiles;
    private String dateModified;
    private int gamePopularityRank;
    private int thumbsUpCount;
    private String name;
    private int mainFileId;
    private int primaryCategoryId;
    private int downloadCount;
    private ModStatus status;
    private List<ModAuthor> authors;
}