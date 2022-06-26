package me.itzg.helpers.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ObjectMappers {
    private static final ObjectMapper DEFAULT = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JavaTimeModule());

    public static ObjectMapper defaultMapper() {
        return DEFAULT;
    }

    private ObjectMappers() {
    }
}
