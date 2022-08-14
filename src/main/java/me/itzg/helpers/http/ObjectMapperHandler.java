package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.hc.core5.http.HttpEntity;

public class ObjectMapperHandler<T> extends LoggingResponseHandler<T> {

    private static final ObjectMapper objectMapper = ObjectMappers.defaultMapper();
    private final Class<T> type;

    public ObjectMapperHandler(Class<T> type) {
        this.type = type;
    }

    @Override
    public T handleEntity(HttpEntity entity) throws IOException {
        return objectMapper.readValue(entity.getContent(), type);
    }
}
