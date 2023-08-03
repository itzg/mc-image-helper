package me.itzg.helpers.find;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import lombok.Getter;

class TrackShallowest implements MatchHandler {

    private int shallowestNameCount;
    @Getter
    private Path shallowest;
    @Getter
    private Path shallowestStartingPoint;

    @Override
    public FileVisitResult handle(Path startingPath, Path matched) {
        final int nameCount = startingPath.relativize(matched).getNameCount();
        if (shallowest == null || nameCount < shallowestNameCount) {
            shallowest = matched;
            shallowestStartingPoint = startingPath;
            shallowestNameCount = nameCount;
        }
        return FileVisitResult.SKIP_SIBLINGS;
    }

    @Override
    public void postDirectory(Path directory, int matchCount, int depth) {
        // n/a
    }

    public boolean matched() {
        return shallowest != null;
    }
}
