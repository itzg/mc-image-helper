package me.itzg.helpers.files.archive;

import java.io.IOException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.compress.archivers.ArchiveException;

/** Helpers for checking output paths and copying archive entries. */
public class ArchiveUtils {

    /**
     * Creates the destination and any missing parents.
     *
     * @param destination directory to prepare
     * @return the absolute, normalized destination path
     * @throws IOException if the directory cannot be created
     */
    static Path prepareDestination(Path destination) throws IOException {
        final Path extractionRoot = destination.toAbsolutePath().normalize();
        Files.createDirectories(extractionRoot);
        return extractionRoot;
    }

    /**
     * Checks whether an entry path points outside the extraction root.
     * Normalizes the path but does not follow symbolic links.
     *
     * @param extractionRoot absolute, normalized extraction directory
     * @param entryName path stored in the archive entry
     * @return {@code true} if the resolved path is outside the root
     */
    static boolean isUnsafeEntry(Path extractionRoot, String entryName) {
        return !extractionRoot.resolve(entryName).normalize().startsWith(extractionRoot);
    }

    /**
     * Builds the output path for an entry, rejecting paths outside the extraction root.
     *
     * @param extractionRoot absolute, normalized destination directory
     * @param entryName path stored in the archive entry
     * @param errorPrefix error message prefix for an unsafe entry
     * @return the normalized output path
     * @throws ArchiveException if the output path escapes the root
     * @see #isUnsafeEntry(Path, String)
     */
    static Path resolveEntry(Path extractionRoot, String entryName, String errorPrefix) throws ArchiveException {
        final Path output = extractionRoot.resolve(entryName).normalize();
        if (!output.startsWith(extractionRoot)) {
            throw new ArchiveException(errorPrefix + entryName);
        }
        return output;
    }

    /**
     * Copies the current entry to its destination, creating parent directories as needed.
     * Existing files are skipped unless overwrite is enabled. Leaves the stream open.
     *
     * @param input archive reader positioned at the entry's contents
     * @param output resolved destination file path
     * @param overwrite whether to replace an existing file rather than skip it
     * @throws IOException if parent creation or copying fails
     */
    static void copyEntry(InputStream input, Path output, boolean overwrite) throws IOException {
        Files.createDirectories(output.getParent());

        if (overwrite) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        } else if (Files.notExists(output)) {
            Files.copy(input, output);
        }
    }
}