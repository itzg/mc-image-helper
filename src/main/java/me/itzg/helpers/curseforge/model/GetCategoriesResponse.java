package me.itzg.helpers.curseforge.model;

import java.util.List;
import lombok.Data;

@Data
public class GetCategoriesResponse {
    private List<Category> data;
}
