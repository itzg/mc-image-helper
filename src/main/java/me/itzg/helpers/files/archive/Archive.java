package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;

public interface Archive {
    Path extract(Path destination, boolean overwrite) throws IOException, ArchiveException;

    boolean containsPathTraversal() throws IOException;

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