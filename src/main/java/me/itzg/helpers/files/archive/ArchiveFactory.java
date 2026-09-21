package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
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
            case "application/zip" -> new Archive(archive, ZipArchiveInputStream::new);
            case "application/x-tar" -> new Archive(archive, TarArchiveInputStream::new);
            case "application/gzip", "application/x-gzip" -> new Archive(archive,
                    input -> new TarArchiveInputStream(new GzipCompressorInputStream(input)));
            case "application/x-bzip2", "application/bz2", "application/x-bzip" -> new Archive(archive,
                    input -> new TarArchiveInputStream(new BZip2CompressorInputStream(input)));
            case "application/zstd", "application/x-zstd" -> new Archive(archive,
                    input -> new TarArchiveInputStream(new ZstdCompressorInputStream(input)));
            default -> throw new IOException("Failed to read file MIME type: " + archive.toAbsolutePath());
        };
    }
}
