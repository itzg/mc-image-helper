package me.itzg.helpers.curseforge.model;

import java.util.List;
import lombok.Data;

@Data
public class ModsSearchResponse {
    List<CurseForgeMod> data;
}
