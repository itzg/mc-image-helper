package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import me.itzg.helpers.json.ObjectMappers;
import org.slf4j.Logger;

public class Manifests {

    private static final String SUFFIX = ".json";

    /**
     * @param oldFiles if null, nothing is done
     * @param currentFiles if null, all old files are removed
     * @param removeListener passed the path of a file being removed. Useful for debug logging as removed.
     */
    public static void cleanup(Path baseDir, Collection<String> oldFiles, Collection<String> currentFiles,
        Consumer<String> removeListener
    )
        throws IOException {
        if (oldFiles == null) {
            return;
        }

        final HashSet<String> filesToRemove = new HashSet<>(oldFiles);
        if (currentFiles != null) {
            filesToRemove.removeAll(currentFiles);
        }
        for (final String fileToRemove : filesToRemove) {
            if (Files.deleteIfExists(baseDir.resolve(fileToRemove))) {
                removeListener.accept(fileToRemove);
            }
        }
    }

    /**
     * @param oldManifest can be null
     */
    public static void cleanup(Path baseDir, BaseManifest oldManifest,
        BaseManifest newManifest, Logger log
    ) throws IOException {
        cleanup(baseDir, oldManifest, newManifest, s -> log.info("Removing old file {}", s));
    }

    /**
     * @param oldManifest can be null
     * @param removeListener passed the path of a file being removed. Useful for debug logging as removed.
     */
    public static void cleanup(Path baseDir, BaseManifest oldManifest, BaseManifest newManifest,
        Consumer<String> removeListener
    ) throws IOException {
        if (oldManifest != null && oldManifest.getFiles() != null) {
            cleanup(baseDir, oldManifest.getFiles(), newManifest.getFiles(), removeListener);
        }
    }

    public static String relativize(Path basePath, Path path) {
        return basePath.relativize(path).toString();
    }

    public static List<String> relativizeAll(Path basePath, Collection<Path> paths) {
        return paths.stream()
            .map(p ->
                basePath.relativize(p)
                .toString()
            )
            .collect(Collectors.toList());
    }

    public static boolean allFilesPresent(Path basePath, BaseManifest manifest) {
        return manifest.getFiles().stream()
            .allMatch(p -> Files.exists(basePath.resolve(p)));
    }

    /**
     *
     * @param outputDir directory where manifest and other module files are based
     * @param id module identifier, such as "fabric"
     */
    public static <M extends BaseManifest> M load(Path outputDir, String id, Class<M> manifestClass) {
        final Path manifestPath = buildManifestPath(outputDir, id);
        if (Files.exists(manifestPath)) {
            final M manifest;
            try {
                manifest = ObjectMappers.defaultMapper().readValue(manifestPath.toFile(), manifestClass);
            } catch (IOException e) {
                throw new ManifestException("Failed to load existing manifest from "+manifestPath, e);
            }

            return manifest;
        }
        else {
            return null;
        }
    }

    private static Path buildManifestPath(Path outputDir, String id) {
        return outputDir.resolve(String.format(
            ".%s-manifest%s", id, SUFFIX
        ));
    }

    public static <M extends BaseManifest> Path save(Path outputDir, String id, M manifest) {
        final Path manifestPath = buildManifestPath(outputDir, id);

        try {
            ObjectMappers.defaultMapper().writeValue(manifestPath.toFile(), manifest);
            return manifestPath;
        } catch (IOException e) {
            throw new ManifestException("Failed to save manifest", e);
        }
    }

    private Manifests() {
    }
}
