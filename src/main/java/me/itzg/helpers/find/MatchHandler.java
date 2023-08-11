package me.itzg.helpers.find;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;

public interface MatchHandler {

    FileVisitResult handle(Path startingPath, Path matched) throws IOException;

    /**
     * @param directory  the directory being concluded
     * @param matchCount the number of matches within this directory and its subdirectories
     * @param depth depth of the directory where zero is one of the starting points
     */
    void postDirectory(Path directory, int matchCount, int depth) throws IOException;
}
