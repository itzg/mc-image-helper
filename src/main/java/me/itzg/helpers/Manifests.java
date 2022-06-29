package me.itzg.helpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class Manifests {

    public static void cleanup(Path baseDir, Set<String> oldFiles, Set<String> currentFiles,
        Consumer<String> removeListener
    )
        throws IOException {
        final HashSet<String> filesToRemove = new HashSet<>(oldFiles);
        filesToRemove.removeAll(currentFiles);
        for (final String fileToRemove : filesToRemove) {
            removeListener.accept(fileToRemove);
            Files.deleteIfExists(baseDir.resolve(fileToRemove));
        }
    }

    private Manifests() {
    }
}
