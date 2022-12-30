package me.itzg.helpers.http;

import java.net.URI;

public class Fetch {

    /**
     * Perform a single fetch (web request) to the given URI/URL.
     */
    public static FetchBuilderBase<?> fetch(URI uri) {
        return new FetchBuilderBase<>(uri);
    }

    /**
     * Provides an efficient way to make multiple web requests since a single client
     * is shared.
     */
    public static SharedFetch sharedFetch(String forCommand) {
        return new SharedFetch(forCommand);
    }

    private Fetch() {
    }
}
