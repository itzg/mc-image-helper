package me.itzg.helpers.vanillatweaks.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PackDefinition {

    Type type;

    String version;

    Map<String,List<String>> packs;
}
