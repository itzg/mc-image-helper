package me.itzg.helpers.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;

public class ObjectListFetchBuilder<T> extends FetchBuilderBase<ObjectListFetchBuilder<T>> {

    private final Class<T> type;
    private final ObjectMapper objectMapper;

    ObjectListFetchBuilder(State state, Class<T> type, ObjectMapper objectMapper) {
        super(state);
        this.type = type;
        this.objectMapper = objectMapper;
    }

    ObjectListFetchBuilder(State state, Class<T> type) {
        this(state, type, ObjectMappers.defaultMapper());
    }

    @Override
    protected void configureRequest(BasicHttpRequest request) throws IOException {
        super.configureRequest(request);
        request.addHeader(HttpHeaders.ACCEPT, "application/json");
    }

    public List<T> execute() throws IOException {
        return useClient(client ->
                client.execute(get(), new ObjectListMapperHandler<>(type, objectMapper))
            );
    }

}
