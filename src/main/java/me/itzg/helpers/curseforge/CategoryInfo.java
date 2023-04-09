package me.itzg.helpers.curseforge;

import lombok.AllArgsConstructor;
import me.itzg.helpers.curseforge.model.Category;

import java.util.Map;

@AllArgsConstructor
class CategoryInfo {
    Map<Integer, Category> contentClassIds;
    int modpackClassId;
}
