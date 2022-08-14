package me.itzg.helpers.http;

import java.net.URI;

public class Fetch {

    public static FetchBuilder<?> fetch(URI uri) {
        return new FetchBuilder<>(uri);
    }
}
