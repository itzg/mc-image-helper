package me.itzg.helpers.properties;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PropertyDefinition {
    String env;
    List<String> allowed;
    Map<String,String> mappings;
    boolean remove;
}
