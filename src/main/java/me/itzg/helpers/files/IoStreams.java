package me.itzg.helpers.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jetbrains.annotations.Nullable;

/**
 * Since Java 8 doesn't have InputStream.transferTo
 */
public class IoStreams {

    @FunctionalInterface
    public interface EntryReader<T> {
        T read(InputStream in) throws IOException;
    }

    /**
     * @return the result of entryReader or null if entry not present
     */
    @Nullable
    public static <T> T readFileFromZip(Path zipFile, String entryName, EntryReader<T> entryReader) throws IOException {
        try (ZipFile zip = ZipFile.builder().setPath(zipFile).get()) {
            final ZipArchiveEntry entry = zip.getEntry(entryName);
            if (entry != null) {
                try (InputStream entryStream = zip.getInputStream(entry)) {
                    return entryReader.read(entryStream);
                }
            }
            else {
                return null;
            }
        }
    }
}
