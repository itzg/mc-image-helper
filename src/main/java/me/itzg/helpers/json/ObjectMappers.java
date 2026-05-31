package me.itzg.helpers.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;

public class ObjectMappers {
    private static final ObjectMapper DEFAULT = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new JavaTimeModule());

    public static ObjectMapper defaultMapper() {
        return DEFAULT;
    }

    private ObjectMappers() {
    }

    public static void outputSchema(Class<?> type) throws JsonProcessingException {
        final ObjectMapper objectMapper = defaultMapper();

        final JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(
            objectMapper
        );

        final JsonNode schema = schemaGen.generateJsonSchema(type);
        System.out.println(objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(schema));
    }
}
