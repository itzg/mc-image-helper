package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * Creates archives for tests. A name ending with {@code /} is added as a directory entry and its content is ignored.
 */
public final class ArchiveTestUtils {

    private ArchiveTestUtils() {
    }

    /**
     * @param archive file to create
     * @param files entry names mapped to their contents
     * @param modified modification time of every entry
     * @return the archive
     */
    public static Path createZip(Path archive, Map<String, String> files, Instant modified) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                final ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(modified.toEpochMilli());
                zos.putNextEntry(entry);
                if (!entry.isDirectory()) {
                    zos.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return archive;
    }

    /**
     * @param archive file to create
     * @param files entry names mapped to their contents
     * @param modified modification time of every entry
     * @return the archive
     */
    public static Path createTarGz(Path archive, Map<String, String> files, Instant modified) throws IOException {
        try (OutputStream output = new GzipCompressorOutputStream(Files.newOutputStream(archive));
            TarArchiveOutputStream tos = new TarArchiveOutputStream(output)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                final TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setModTime(modified.toEpochMilli());
                if (entry.isDirectory()) {
                    tos.putArchiveEntry(entry);
                } else {
                    final byte[] data = file.getValue().getBytes(StandardCharsets.UTF_8);
                    entry.setSize(data.length);
                    tos.putArchiveEntry(entry);
                    tos.write(data);
                }
                tos.closeArchiveEntry();
            }
        }
        return archive;
    }
}
