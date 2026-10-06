package me.itzg.helpers.files.archive;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

/**
 * Chooses the archive reader and decompressor from the file's contents, independent of its name or OS MIME mappings.
 * Supports ZIP, TAR, TAR.GZ, TAR.BZ2, and TAR.ZST.
 */
public final class ArchiveFactory {

    private static final int TAR_RECORD_SIZE = 512;

    /**
     * Creates an archive helper for the detected file type, without reading its entries.
     *
     * @param archive source archive path
     * @return an archive helper with the appropriate reader
     * @throws IllegalArgumentException if the path does not exist or is not a regular file
     * @throws IOException if the file type cannot be detected or is unsupported
     */
    static Archive create(Path archive) throws IOException {
        if (!Files.exists(archive)) {
            throw new IllegalArgumentException("File does not exist: " + archive.toAbsolutePath());
        } else if (!Files.isRegularFile(archive)) {
            throw new IllegalArgumentException("File is not a regular file: " + archive.toAbsolutePath());
        }

        try (var input = new BufferedInputStream(Files.newInputStream(archive))) {
            return new Archive(archive, detectOpener(input));
        }
    }

    private static ArchiveOpener detectOpener(BufferedInputStream input) throws IOException {
        final String format = detectArchive(input);
        if (format != null) {
            return switch (format) {
                case ArchiveStreamFactory.ZIP -> ZipArchiveInputStream::new;
                case ArchiveStreamFactory.TAR -> TarArchiveInputStream::new;
                default -> throw new IOException("Unsupported archive format: " + format + "; expected ZIP or TAR");
            };
        }

        final String compression = detectCompression(input);
        if (compression == null) {
            if (isEmptyTar(input)) {
                return TarArchiveInputStream::new;
            }
            throw new IOException("Unrecognized archive signature; expected ZIP, TAR, TAR.GZ, TAR.BZ2 or TAR.ZST");
        }

        try (var payload = new BufferedInputStream(decompress(compression, input))) {
            final String payloadFormat = detectArchive(payload);
            if (ArchiveStreamFactory.TAR.equals(payloadFormat) || (payloadFormat == null && isEmptyTar(payload))) {
                // Archive owns the raw stream, including when constructing the decompressor fails.
                return source -> new TarArchiveInputStream(decompress(compression, source));
            }
            throw new IOException("Unsupported " + compression + " compressed payload: "
                    + (payloadFormat == null ? "unrecognized archive signature" : payloadFormat) + "; expected TAR");
        }
    }

    private static String detectArchive(BufferedInputStream input) throws IOException {
        try {
            return ArchiveStreamFactory.detect(input);
        } catch (ArchiveException e) {
            // If the factory fails to detect a signature, e.g. the signature is read but no 
            // match, throws IOException with no cause, return null detected type
            // If the factory fails to read, throws same IOException with cause.
            if (e.getCause() != null) {
                throw new IOException("Failed to read archive signature", e);
            }
            return null;
        }
    }

    private static String detectCompression(BufferedInputStream input) throws IOException {
        try {
            return CompressorStreamFactory.detect(input);
        } catch (CompressorException e) {
            // Same situation as detectArchive()
            if (e.getCause() != null) {
                throw new IOException("Failed to read compression signature", e);
            }
            return null;
        }
    }

    private static InputStream decompress(String compression, InputStream input) throws IOException {
        return switch (compression) {
            case CompressorStreamFactory.GZIP -> new GzipCompressorInputStream(input);
            case CompressorStreamFactory.BZIP2 -> new BZip2CompressorInputStream(input);
            case CompressorStreamFactory.ZSTANDARD -> new ZstdCompressorInputStream(input);
            default -> throw new IOException("Unsupported compression format: " + compression
                    + "; expected gzip, bzip2 or Zstandard");
        };
    }

    /**
     * Empty TARs have no header signature.
     *
     * An empty TAR has no header to detect. The end-of-file is marked by 2 x 512 zero filled blocks. As such, check that 
     * an empty tar contains a minimum of 2 x 512 empty blocks, and return false if any non-zero data is encountered 
     */
    private static boolean isEmptyTar(InputStream input) throws IOException {
        final byte[] record = new byte[TAR_RECORD_SIZE];
        long records = 0;
        int length;
        while ((length = input.readNBytes(record, 0, record.length)) != 0) {
            if (length != record.length) {
                return false;
            }
            for (byte value : record) {
                if (value != 0) {
                    return false;
                }
            }
            records++;
        }
        return records >= 2;
    }
}