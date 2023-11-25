package me.itzg.helpers.patch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.core.util.Separators.Spacing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.util.Map;

public class JsonFileFormat implements FileFormat {

    private static final String[] SUFFIXES = {"json"};
    private static final TypeReference<Map<String, Object>> MAP_TYPE
            = new TypeReference<Map<String, Object>>() {
    };

    private final ObjectMapper objectMapper;
    private final ObjectWriter objectWriter;

    public JsonFileFormat() {
        objectMapper = new ObjectMapper();
        objectWriter = objectMapper.writer(
                new DefaultPrettyPrinter()
                    .withSeparators(
                        Separators.createDefaultInstance()
                            .withObjectFieldValueSpacing(Spacing.NONE)
                    )
        );
    }

    @Override
    public String[] getFileSuffixes() {
        return SUFFIXES;
    }

    @Override
    public String getName() {
        return "json";
    }

    @Override
    public Map<String, Object> decode(String content) throws IOException {
        return objectMapper.readValue(content, MAP_TYPE);
    }

    @Override
    public String encode(Map<String, Object> content) throws IOException {
        return objectWriter.writeValueAsString(content);
    }
}
