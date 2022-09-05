package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.List;
import org.apache.hc.core5.http.HttpEntity;

public class ObjectListMapperHandler<T> extends LoggingResponseHandler<List<T>> {

    private final ObjectMapper objectMapper;
    private final Class<T> type;

    public ObjectListMapperHandler(Class<T> type, ObjectMapper objectMapper) {
        this.type = type;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<T> handleEntity(HttpEntity entity) throws IOException {
        final ObjectReader objectReader = objectMapper.readerForListOf(type);
        return objectReader.readValue(entity.getContent());
    }
}
