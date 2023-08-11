package me.itzg.helpers.curseforge;

import java.util.Map;
import lombok.AllArgsConstructor;
import me.itzg.helpers.curseforge.model.Category;

@AllArgsConstructor
public class CategoryInfo {
    Map<Integer, Category> contentClassIds;
    Map<String, Integer> slugIds;

    public int getClassIdForSlug(String categorySlug) {
        final Integer classId = slugIds.get(categorySlug);
        if (classId != null) {
            return classId;
        }
        else {
            throw new IllegalArgumentException("Unexpected category: " + categorySlug);
        }
    }

    public Category getCategory(int categoryId) {
        final Category category = contentClassIds.get(categoryId);
        if (category != null) {
            return category;
        }
        else {
            throw new IllegalArgumentException("Unknown category ID: " + categoryId);
        }
    }
}
