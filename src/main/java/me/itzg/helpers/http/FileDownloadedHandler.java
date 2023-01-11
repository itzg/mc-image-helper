package me.itzg.helpers.http;

import java.net.URI;
import java.nio.file.Path;

@FunctionalInterface
public interface FileDownloadedHandler {

    void call(URI uri, Path file, long contentSizeBytes);
}
