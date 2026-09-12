package me.itzg.helpers.files.archive;

import java.io.IOException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.compress.archivers.ArchiveException;

public class ArchiveUtils {
    static Path prepareDestination(Path destination) throws IOException {
        final Path extractionRoot = destination.toAbsolutePath().normalize();
        Files.createDirectories(extractionRoot);
        return extractionRoot;
    }

    static boolean isUnsafeEntry(Path extractionRoot, String entryName) {
        return !extractionRoot.resolve(entryName).normalize().startsWith(extractionRoot);
    }

    static Path resolveEntry(Path extractionRoot, String entryName, String errorPrefix) throws ArchiveException {
        final Path output = extractionRoot.resolve(entryName).normalize();
        if (!output.startsWith(extractionRoot)) {
            throw new ArchiveException(errorPrefix + entryName);
        }
        return output;
    }

    static void copyEntry(InputStream input, Path output, boolean overwrite) throws IOException {
        Files.createDirectories(output.getParent());

        if (overwrite) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        } else if (Files.notExists(output)) {
            Files.copy(input, output);
        }
    }
}