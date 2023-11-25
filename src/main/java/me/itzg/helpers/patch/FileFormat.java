package me.itzg.helpers.patch;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.Map;

public interface FileFormat {
    TypeReference<Map<String, Object>> MAP_TYPE
        = new TypeReference<Map<String, Object>>() {};

    /**
     * @return supported file suffixes, without leading dot
     */
    String[] getFileSuffixes();

    String getName();

    Map<String, Object> decode(String content) throws IOException;

    String encode(Map<String, Object> content) throws IOException;
}
