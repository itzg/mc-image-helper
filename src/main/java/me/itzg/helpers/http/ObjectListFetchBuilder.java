package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;

public class ObjectListFetchBuilder<T> extends FetchBuilder<ObjectListFetchBuilder<T>> {

    private final Class<T> type;
    private final ObjectMapper objectMapper;

    ObjectListFetchBuilder(Config config, Class<T> type, ObjectMapper objectMapper) {
        super(config);
        this.type = type;
        this.objectMapper = objectMapper;
    }

    ObjectListFetchBuilder(Config config, Class<T> type) {
        this(config, type, ObjectMappers.defaultMapper());
    }

    @Override
    protected void configureRequest(BasicHttpRequest request) throws IOException {
        super.configureRequest(request);
        request.addHeader(HttpHeaders.ACCEPT, "application/json");
    }

    public List<T> execute() throws IOException {
        try (CloseableHttpClient client = client()) {
            return client.execute(get(), new ObjectListMapperHandler<>(type, objectMapper));
        }
    }

}
