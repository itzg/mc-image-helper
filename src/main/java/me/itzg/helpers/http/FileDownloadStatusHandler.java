package me.itzg.helpers.http;

import java.net.URI;
import java.nio.file.Path;

@FunctionalInterface
public interface FileDownloadStatusHandler {

    void call(FileDownloadStatus status, URI uri, Path file);
}
