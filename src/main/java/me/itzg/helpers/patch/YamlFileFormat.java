package me.itzg.helpers.patch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.util.Map;

public class YamlFileFormat implements FileFormat {

    private static final String[] SUFFIXES = {"yaml", "yml"};
    private static final TypeReference<Map<String, Object>> MAP_TYPE
            = new TypeReference<Map<String, Object>>() {};

    private final YAMLMapper objectMapper;

    public YamlFileFormat() {
        objectMapper = new YAMLMapper()
                .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
    }

    @Override
    public String[] getFileSuffixes() {
        return SUFFIXES;
    }

    @Override
    public String getName() {
        return "yaml";
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
