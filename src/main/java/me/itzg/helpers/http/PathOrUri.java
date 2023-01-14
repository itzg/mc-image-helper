package me.itzg.helpers.http;

import java.net.URI;
import java.nio.file.Path;
import lombok.Getter;
import lombok.ToString;

@ToString
public class PathOrUri {
    @Getter
    final Path path;
    @Getter
    final URI uri;

    public static PathOrUri path(Path path) {
        return new PathOrUri(path, null);
    }

    public static PathOrUri uri(URI uri) {
        return new PathOrUri(null, uri);
    }

    protected PathOrUri(Path path, URI uri) {
        this.path = path;
        this.uri = uri;
    }
}
