package me.itzg.helpers.http;

import java.net.URI;
import me.itzg.helpers.http.SharedFetch.Options;
import org.slf4j.Logger;

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
    public static SharedFetch sharedFetch(String forCommand, Options options) {
        return new SharedFetch(forCommand, options);
    }

    private Fetch() {
    }

    public static FileDownloadStatusHandler loggingDownloadStatusHandler(Logger log) {
        return (status, uri, file) -> {
            switch (status) {
                case DOWNLOADING:
                    log.debug("Downloading {}", file);
                    break;
                case DOWNLOADED:
                    log.info("Downloaded {}", file);
                    break;
                case SKIP_FILE_UP_TO_DATE:
                    log.info("The file {} is already up to date", file);
                    break;
            }
        };
    }
}
