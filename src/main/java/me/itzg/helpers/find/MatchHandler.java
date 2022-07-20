package me.itzg.helpers.find;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;

@FunctionalInterface
public interface MatchHandler {

    FileVisitResult handle(Path startingPath, Path matched);
}
