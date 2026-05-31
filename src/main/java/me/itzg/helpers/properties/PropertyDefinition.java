package me.itzg.helpers.properties;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class PropertyDefinition {
    String env;
    List<String> allowed;
    Map<String,String> mappings;
    boolean remove;
    /**
     * Converts bashlash-n's in the text to actual newlines.
     * Resolves the case wehre Docker env files cannot contain real newlines in the value.
     */
    boolean translateLiteralNewlines;
}
