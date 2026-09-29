package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ArchiveFactory {

    static Archive create(Path archive) throws IOException {
        if (!Files.exists(archive)) {
            throw new IllegalArgumentException("File does not exist: " + archive.toAbsolutePath());
        } else if (!Files.isRegularFile(archive)) {
            throw new IllegalArgumentException("File is not a regular file: " + archive.toAbsolutePath());
        }

        final String contentType = Files.probeContentType(archive);
        log.debug("Detected archive MIME type '{}' for {}", contentType, archive);

        if (contentType == null) {
            throw new IOException("Failed to read file MIME type: " + archive.toAbsolutePath());
        }

        switch (contentType) {
            case "application/zip":
                return new Archive(archive, ZipArchiveInputStream::new);
            case "application/x-tar":
                return new Archive(archive, TarArchiveInputStream::new);
            case "application/gzip":
            case "application/x-gzip":
                return new Archive(archive,
                        input -> new TarArchiveInputStream(new GzipCompressorInputStream(input)));
            case "application/x-bzip2":
            case "application/bz2":
            case "application/x-bzip":
                return new Archive(archive,
                        input -> new TarArchiveInputStream(new BZip2CompressorInputStream(input)));
            case "application/zstd":
            case "application/x-zstd":
                return new Archive(archive,
                        input -> new TarArchiveInputStream(new ZstdCompressorInputStream(input)));
            default:
                throw new IOException("Failed to read file MIME type: " + archive.toAbsolutePath());
        }
    }
}
