package me.itzg.helpers.files;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.json.ObjectMappers;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Slf4j
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

    /**
     * @param paths paths to process where nulls are skipped
     */
    public static List<String> relativizeAll(Path basePath, Collection<Path> paths) {
        return relativizeAll(basePath, paths.stream());
    }

    /**
     * @param paths paths to process where nulls are skipped
     */
    public static List<String> relativizeAll(Path basePath, Path... paths) {
        return relativizeAll(basePath, Stream.of(paths));
    }

    /**
     * @param pathStream paths to process where nulls are skipped
     */
    public static List<String> relativizeAll(Path basePath, Stream<Path> pathStream) {
        return pathStream
            .filter(Objects::nonNull)
            .map(p -> {
                    try {
                        return basePath.relativize(p)
                            .toString();
                    } catch (IllegalArgumentException e) {
                        throw new GenericException(String.format("Failed to relative %s against %s", p, basePath),
                            e
                        );
                    }
                }
            )
            .collect(Collectors.toList());
    }

    public static List<String> missingFiles(Path basePath, BaseManifest manifest) {
        return manifest.getFiles().stream()
            .filter(p -> !Files.exists(basePath.resolve(p)))
            .collect(Collectors.toList());
    }

    public static boolean allFilesPresent(Path basePath, BaseManifest manifest) {
        return allFilesPresent(basePath, manifest, null);
    }

    /**
     * @param ignoreMissingFiles relative paths of files to ignore if they're missing
     */
    public static boolean allFilesPresent(Path basePath, BaseManifest manifest, @Nullable List<String> ignoreMissingFiles) {
        return manifest.getFiles().stream()
            .allMatch(p ->
                    (ignoreMissingFiles != null && ignoreMissingFiles.contains(p))
                || Files.exists(basePath.resolve(p))
            );
    }

    /**
     *
     * @param outputDir directory where manifest and other module files are based
     * @param id module identifier, such as "fabric"
     * @return the loaded manifest file or null if it didn't exist or was invalid content
     */
    public static <M extends BaseManifest> M load(Path outputDir, String id, Class<M> manifestClass) {
        final Path manifestPath = buildManifestPath(outputDir, id);
        if (Files.exists(manifestPath)) {
            final M manifest;
            try {
                manifest = ObjectMappers.defaultMapper().readValue(manifestPath.toFile(), manifestClass);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse existing manifest file {}", manifestPath, e);
                return null;
            } catch (IOException e) {
                throw new ManifestException("Failed to load existing manifest from "+manifestPath, e);
            }

            return manifest;
        }
        else {
            return null;
        }
    }

    public static Path buildManifestPath(Path outputDir, String id) {
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

    public static void remove(Path outputDir, String id) {
        final Path manifestPath = buildManifestPath(outputDir, id);
        if (Files.exists(manifestPath)) {
            try {
                Files.delete(manifestPath);
            } catch (IOException e) {
                throw new ManifestException("Failed to remove manifest file", e);
            }
        }
    }
}
