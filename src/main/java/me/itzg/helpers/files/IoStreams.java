package me.itzg.helpers.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return entryReader.read(zipIn);
                }
            }
        }

        return null;
    }
}
