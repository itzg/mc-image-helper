package me.itzg.helpers.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

public class ObjectMapperFileFormat implements FileFormat {

    private final ObjectMapper objectMapper;
    private final String name;
    private final String[] fileSuffixes;

    protected ObjectMapperFileFormat(ObjectMapper objectMapper, String name, String... fileSuffixes) {
        this.objectMapper = objectMapper;
        this.name = name;
        this.fileSuffixes = fileSuffixes;
    }

    @Override
    public String[] getFileSuffixes() {
        return fileSuffixes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, Object> decode(String content) throws IOException {
        return objectMapper.readValue(content, MAP_TYPE);
    }

    @Override
    public String encode(Map<String, Object> content) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
    }
}
