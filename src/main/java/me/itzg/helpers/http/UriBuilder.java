package me.itzg.helpers.http;

import java.net.URI;
import lombok.Getter;

public class UriBuilder {

    @Getter
    private final String baseUrl;

    protected UriBuilder(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static UriBuilder withBaseUrl(String baseUrl) {
        return new UriBuilder(baseUrl);
    }

    @SuppressWarnings("unused")
    public static UriBuilder withNoBaseUrl() {
        return new UriBuilder("");
    }

    public URI resolve(String path, Object... values) {
        return Uris.populateToUri(baseUrl + path, values);
    }

    public URI resolve(String path, Uris.QueryParameters queryParameters, Object... values) {
        return Uris.populateToUri(baseUrl + path, queryParameters, values);
    }

}
