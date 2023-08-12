package me.itzg.helpers.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import me.itzg.helpers.errors.GenericException;
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
        try (ZipFile zipFileReader = new ZipFile(zipFile.toFile())) {
            return zipFileReader.stream()
                .filter(zipEntry -> zipEntry.getName().equals(entryName))
                .findFirst()
                .map(zipEntry -> {
                    try {
                        return entryReader.read(zipFileReader.getInputStream(zipEntry));
                    } catch (IOException e) {
                        throw new GenericException("Getting entry input stream from zip file", e);
                    }
                })
                .orElse(null);
        }
    }
}
