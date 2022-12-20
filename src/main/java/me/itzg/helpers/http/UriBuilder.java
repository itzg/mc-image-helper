package me.itzg.helpers.http;

import java.net.URI;

public class UriBuilder {

    private final String baseUrl;

    protected UriBuilder(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static UriBuilder withBaseUrl(String baseUrl) {
        return new UriBuilder(baseUrl);
    }

    public static UriBuilder withNoBaseUrl() {
        return new UriBuilder("");
    }

    public URI resolve(String path, String... variables) {
        return Uris.populateToUri(baseUrl + path, variables);
    }
}
