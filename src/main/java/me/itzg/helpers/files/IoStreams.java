package me.itzg.helpers.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Since Java 8 doesn't have InputStream.transferTo
 */
public class IoStreams {

    public static void transfer(InputStream in, OutputStream out) throws IOException {
        final byte[] buf = new byte[8192];
        int length;
        while ((length = in.read(buf)) != -1) {
           out.write(buf, 0, length);
        }
    }

    @FunctionalInterface
    public interface EntryReader<T> {
        T read(InputStream in) throws IOException;
    }

    public static <T> T readFileFromZip(Path zipFile, String entryName, EntryReader<T> reader) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return reader.read(zipIn);
                }
            }
        }

        return null;
    }
}
