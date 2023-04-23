package me.itzg.helpers.http;

import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClientForm;

import java.util.function.Consumer;

@Slf4j
public class FormFetchBuilder extends FetchBuilderBase<FormFetchBuilder> {
    private final Consumer<HttpClientForm> formCallback;

    protected FormFetchBuilder(State state, Consumer<HttpClientForm> formCallback) {
        super(state);
        this.formCallback = formCallback;
    }

    public <T> ObjectFetchBuilder<T> toObject(Class<T> type) {
        return super.toObject(type, client -> client
                .headers(this::applyHeaders)
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "form post"))
                .post()
                .uri(uri())
                .sendForm((httpClientRequest, httpClientForm) ->
                    formCallback.accept(httpClientForm)
                )
        );
    }
}
