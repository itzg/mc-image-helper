package me.itzg.helpers.files;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.json.ObjectMappers;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "manifest-files",
    description = "Prints the file entries stored in a manifest, one per line")
public class ManifestFilesCommand implements Callable<Integer> {

    @SuppressWarnings("unused")
    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean showHelp;

    @Option(names = "--scope", paramLabel = "SCOPE",
        description = "MANIFEST is the directory that holds the scoped manifest"
            + "%nReads .<SCOPE>-manifest.json (same path mcopy writes)"
    )
    String scope;

    // fallbackValue=true so `--sort` stays true; picocli otherwise inverts negatable flags whose default is true
    @Option(names = "--sort", negatable = true, defaultValue = "true", fallbackValue = "true",
        description = "Sort entries before printing"
            + "%nDefault: ${DEFAULT-VALUE}. Use --no-sort to keep manifest order"
    )
    boolean sort;

    @Parameters(arity = "1", paramLabel = "MANIFEST",
        description = "Manifest file to read. With --scope, the directory that contains the manifest")
    Path manifest;

    @Override
    public Integer call() {
        final BaseManifest loaded = loadManifest(resolveManifestPath());
        printFiles(loaded);
        return ExitCode.OK;
    }

    private Path resolveManifestPath() {
        if (scope == null) {
            return manifest;
        }
        if (scope.isBlank()) {
            throw new InvalidParameterException("--scope requires a non-blank scope name");
        }
        if (!Files.isDirectory(manifest)) {
            throw new InvalidParameterException(
                "With --scope, the path must be the directory containing the manifest: " + manifest);
        }
        return Manifests.buildManifestPath(manifest, scope);
    }

    /**
     * Same mapper and {@link BaseManifest} model as {@link Manifests#load}, but a missing or
     * unreadable file is an error instead of being treated as "no manifest".
     */
    private static BaseManifest loadManifest(Path manifestPath) {
        if (!Files.exists(manifestPath)) {
            throw new InvalidParameterException("Manifest file does not exist: " + manifestPath);
        }
        if (!Files.isRegularFile(manifestPath)) {
            throw new InvalidParameterException("Manifest path is not a file: " + manifestPath);
        }

        try {
            final BaseManifest loaded = ObjectMappers.defaultMapper()
                .readValue(manifestPath.toFile(), BaseManifest.class);
            if (loaded == null) {
                throw new InvalidParameterException("Failed to parse manifest file " + manifestPath);
            }
            return loaded;
        } catch (JsonProcessingException e) {
            throw new InvalidParameterException(
                "Failed to parse manifest file " + manifestPath + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new InvalidParameterException(
                "Failed to read manifest file " + manifestPath + ": " + e.getMessage(), e);
        }
    }

    private void printFiles(BaseManifest loaded) {
        final Collection<String> files = loaded.getFiles();
        if (files == null || files.isEmpty()) {
            return;
        }

        final List<String> entries = new ArrayList<>(files);
        if (sort) {
            entries.sort(Comparator.naturalOrder());
        }
        for (final String entry : entries) {
            System.out.println(entry);
        }
    }
}
