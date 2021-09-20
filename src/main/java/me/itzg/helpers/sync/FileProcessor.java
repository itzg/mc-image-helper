package me.itzg.helpers.sync;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface FileProcessor {
    void processFile(Path srcFile, Path destFile) throws IOException;
}
