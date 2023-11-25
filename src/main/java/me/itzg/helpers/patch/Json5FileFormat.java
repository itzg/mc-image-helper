package me.itzg.helpers.patch;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.core.util.Separators.Spacing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.Map;

public class Json5FileFormat implements FileFormat {

    private static final String[] SUFFIXES = {"json5"};
    private static final TypeReference<Map<String, Object>> MAP_TYPE
            = new TypeReference<Map<String, Object>>() {
    };

    private final ObjectMapper objectMapper;
    private final ObjectWriter objectWriter;

    // Currently, missing additional whitespace character support and hexadecimal numbers
    // Check https://github.com/FasterXML/jackson-core/wiki/JsonReadFeatures to see if/when they become available
    public Json5FileFormat() {
        objectMapper = JsonMapper.builder().enable(
                JsonReadFeature.ALLOW_TRAILING_COMMA,
                JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS,
                JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS,
                JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS,
                JsonReadFeature.ALLOW_JAVA_COMMENTS,
                JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
                JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS,
                JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES,
                JsonReadFeature.ALLOW_SINGLE_QUOTES,
                JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS
        ).build();

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
        return "json5";
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
