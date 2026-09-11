package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArchiveFactory {

    static Archive create(Path archive) throws IOException {
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
            case "application/x-tar" -> new Tar(archive);
            case "application/gzip", "application/x-gzip" -> new TarGzip(archive);
            case "application/x-bzip2", "application/bz2", "application/x-bzip" -> new TarBzip2(archive);
            case "application/zstd", "application/x-zstd" -> new TarZstd(archive);
            default -> throw new IOException("Failed to read file MIME type: " + archive.toAbsolutePath());
        };
    }

}