package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

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
            case "application/x-tar" -> new TarArchive(archive, Files::newInputStream);
            case "application/gzip", "application/x-gzip" -> new TarArchive(archive, path -> new GzipCompressorInputStream(Files.newInputStream(path)));
            case "application/x-bzip2", "application/bz2", "application/x-bzip" -> new TarArchive(archive, path -> new BZip2CompressorInputStream(Files.newInputStream(path)));
            case "application/zstd", "application/x-zstd" -> new TarArchive(archive, path -> new ZstdCompressorInputStream(Files.newInputStream(path)));
            default -> throw new IOException("Failed to read file MIME type: " + archive.toAbsolutePath());
        };
    }
}