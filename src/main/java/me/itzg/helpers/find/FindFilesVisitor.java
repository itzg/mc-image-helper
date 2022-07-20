package me.itzg.helpers.find;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FindFilesVisitor implements FileVisitor<Path> {

    private final FindType type;
    private final List<PathMatcher> names;
    private final List<PathMatcher> excludeNames;
    private final Path startingPoint;
    private final MatchHandler handleMatch;
    private int matchCount;

    public FindFilesVisitor(FindType type, List<PathMatcher> names, List<PathMatcher> excludeNames,
        Path startingPoint, MatchHandler handleMatch
    ) {

        this.type = type;
        this.names = names;
        this.excludeNames = excludeNames;
        this.startingPoint = startingPoint;
        this.handleMatch = handleMatch;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        final Path dirName = dir.getFileName();

        if (excludeNames != null &&
            excludeNames.stream().anyMatch(pathMatcher -> pathMatcher.matches(dirName))
        ) {
            return FileVisitResult.SKIP_SUBTREE;
        }

        if (type == FindType.directory &&
            names != null &&
            names.stream().anyMatch(pathMatcher -> pathMatcher.matches(dirName))
        ) {
            return handleMatch.handle(startingPoint, dir);
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        if (type == FindType.directory) {
            // this method is only given files, so skip them all
            return FileVisitResult.CONTINUE;
        }

        final Path fileName = path.getFileName();

        if (excludeNames != null &&
            excludeNames.stream().anyMatch(pathMatcher -> pathMatcher.matches(fileName))) {
            return FileVisitResult.CONTINUE;
        }

        if (names != null &&
            names.stream().anyMatch(pathMatcher -> pathMatcher.matches(fileName))) {
            ++matchCount;
            return handleMatch.handle(startingPoint, path);
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        log.warn("Failed to visit file {} due to {}", file, exc.getMessage());
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return FileVisitResult.CONTINUE;
    }

    public int getMatchCount() {
        return matchCount;
    }
}
