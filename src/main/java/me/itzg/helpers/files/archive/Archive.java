package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.compress.archivers.ArchiveException;

public interface Archive {
    Path extract(Path destination, boolean overwrite) throws IOException, ArchiveException;

    boolean containsPathTraversal() throws IOException;

    default Path prepareDestination(Path destination) throws IOException {
        final Path extractionRoot = destination.toAbsolutePath().normalize();
        Files.createDirectories(extractionRoot);
        return extractionRoot;
    }

    default boolean isUnsafeEntry(Path extractionRoot, String entryName) {
        return !extractionRoot.resolve(entryName).normalize().startsWith(extractionRoot);
    }

    default Path resolveEntry(Path extractionRoot, String entryName, String errorPrefix) throws ArchiveException {
        final Path output = extractionRoot.resolve(entryName).normalize();
        if (!output.startsWith(extractionRoot)) {
            throw new ArchiveException(errorPrefix + entryName);
        }
        return output;
    }

    default void copyEntry(InputStream input, Path output, boolean overwrite) throws IOException {
        Files.createDirectories(output.getParent());

        if (overwrite) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        } else if (Files.notExists(output)) {
            Files.copy(input, output);
        }
    }

    static Archive parseArchive(Path archive) throws IOException {
        if (!Files.exists(archive)) {
            throw new IllegalArgumentException("File does not exist: " + archive.toAbsolutePath());
        } else if (!Files.isRegularFile(archive)) {
            throw new IllegalArgumentException("File is not a regular file: " + archive.toAbsolutePath());
        }

        final String contentType = Files.probeContentType(archive);

        if (contentType == null) {
            throw new IOException("Failed to read file MIME type: " + archive.toAbsolutePath());
        }

        return switch (contentType) {
            case "application/zip" -> new Zip(archive);
            default -> throw new IOException("Failed to read file MIME type: " + archive.toAbsolutePath());
        };
    }
}