package me.itzg.helpers.http;

import java.io.IOException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;

public class ObjectFetchBuilder<T> extends FetchBuilder<ObjectFetchBuilder<T>> {

    private final Class<T> type;

    ObjectFetchBuilder(FetchBuilder.Config config, Class<T> type) {
        super(config);
        this.type = type;
    }

    @Override
    protected void configureRequest(BasicHttpRequest request) throws IOException {
        super.configureRequest(request);
        request.addHeader(HttpHeaders.ACCEPT, "application/json");
    }

    public T execute() throws IOException {
        try (CloseableHttpClient client = client()) {
            return client.execute(get(), new ObjectMapperHandler<>(type));
        }
    }

}
