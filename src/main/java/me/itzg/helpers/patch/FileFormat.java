package me.itzg.helpers.patch;

import java.io.IOException;
import java.util.Map;

public interface FileFormat {
    /**
     * @return supported file suffixes, without leading dot
     */
    String[] getFileSuffixes();

    String getName();

    Map<String, Object> decode(String content) throws IOException;

    String encode(Map<String, Object> content) throws IOException;
}
