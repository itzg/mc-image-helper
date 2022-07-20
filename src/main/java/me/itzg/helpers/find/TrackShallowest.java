package me.itzg.helpers.find;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;

class TrackShallowest implements MatchHandler {

    private int shallowestNameCount;
    private Path shallowest;
    private Path shallowestStartingPoint;

    public Path getShallowest() {
        return shallowest;
    }

    public Path getShallowestStartingPoint() {
        return shallowestStartingPoint;
    }

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

    public boolean matched() {
        return shallowest != null;
    }
}
