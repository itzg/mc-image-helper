package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileTreeSnapshot {

    private final Path dir;
    private final Set<String> files;

    protected FileTreeSnapshot(Path dir, Set<String> files) {
        this.dir = dir;
        this.files = files;
    }

    public static FileTreeSnapshot takeSnapshot(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return new FileTreeSnapshot(
                dir,
            stream.filter(Files::isRegularFile)
                .map(path -> dir.relativize(path).toString())
                .collect(Collectors.toSet())
            );
        }
    }

    public Set<String> findNewFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                .map(path -> dir.relativize(path).toString())
                .filter(relPath -> !files.contains(relPath))
                .collect(Collectors.toSet());
        }
    }

}
