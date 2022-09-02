package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;

public class ObjectFetchBuilder<T> extends FetchBuilder<ObjectFetchBuilder<T>> {

    private final Class<T> type;
    private final ObjectMapper objectMapper;

    ObjectFetchBuilder(Config config, Class<T> type, ObjectMapper objectMapper) {
        super(config);
        this.type = type;
        this.objectMapper = objectMapper;
    }

    ObjectFetchBuilder(Config config, Class<T> type) {
        this(config, type, ObjectMappers.defaultMapper());
    }

    @Override
    protected void configureRequest(BasicHttpRequest request) throws IOException {
        super.configureRequest(request);
        request.addHeader(HttpHeaders.ACCEPT, "application/json");
    }

    public T execute() throws IOException {
        try (CloseableHttpClient client = client()) {
            return client.execute(get(), new ObjectMapperHandler<>(type, objectMapper));
        }
    }

}
