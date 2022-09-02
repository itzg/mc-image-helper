package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.hc.core5.http.HttpEntity;

public class ObjectMapperHandler<T> extends LoggingResponseHandler<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> type;

    public ObjectMapperHandler(Class<T> type, ObjectMapper objectMapper) {
        this.type = type;
        this.objectMapper = objectMapper;
    }

    @Override
    public T handleEntity(HttpEntity entity) throws IOException {
        return objectMapper.readValue(entity.getContent(), type);
    }
}
