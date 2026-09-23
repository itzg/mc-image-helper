package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Safely extract ZIP, TAR, TAR.GZ, TAR.BZ2 and TAR.ZST 
 */
@Slf4j
@AllArgsConstructor
public final class Archive {

    private final Path archive;
    private final ArchiveOpener opener;

    /**
     * Checks for path traversal without extracting files. Logs the first unsafe entry.
     *
     * @return {@code true} if a path-traversal entry is found; otherwise {@code false}
     * @throws IOException if the archive cannot be opened or read
     */
    public boolean containsPathTraversal() throws IOException {
        final String unsafeEntry = findUnsafeEntry();

        if (unsafeEntry != null) {
            log.warn("Path traversal detected at: " + unsafeEntry + " in archive: " + archive.toAbsolutePath());
            return true;
        }

        return false;
    }

    /**
     * Extracts everything, keeping the archive's directory structure.
     * Checks for path traversal before writing any files.
     *
     * @param destination output directory
     * @param overwrite whether to replace existing files
     * @return the absolute, normalized destination path
     * @throws IOException if reading or extracting the archive fails
     * @throws ArchiveException if an entry contains path traversal
     * @see #extract(Path, List, boolean)
     */
    public Path extract(Path destination, boolean overwrite) throws IOException, ArchiveException {
        return extract(destination, List.of(), overwrite);
    }

    /**
     * Extracts selected files, keeping their paths within the output directory.
     * Names must match exactly, including case. Checks all entries for path 
     * traversal and verifies requested files exist before extraction.
     *
     * @param destination output directory
     * @param files non-null list of file names; an empty list extracts everything
     * @param overwrite whether to replace existing files; otherwise existing files are skipped
     * @return the absolute, normalized destination path
     * @throws IOException if reading or extracting the archive fails
     * @throws ArchiveException if an entry contains path traversal or requested files are missing
     */
    public Path extract(Path destination, List<String> files, boolean overwrite)
            throws IOException, ArchiveException {
        final Set<String> selected = new LinkedHashSet<>(files);
        validateForExtraction(selected);

        final Path extractionRoot = ArchiveUtils.prepareDestination(destination);

        try (InputStream source = Files.newInputStream(archive);
                var input = opener.open(source)) {
            ArchiveEntry entry;

            while ((entry = input.getNextEntry()) != null) {
                // "If we are selectively extracting, skip directories and files that were not requested"
                if (!selected.isEmpty() && (entry.isDirectory() || !selected.contains(entry.getName()))) {
                    continue;
                }
                final Path output = ArchiveUtils.resolveEntry(extractionRoot, entry.getName(),
                        "Invalid Archive Entry; contains path traversal: ");

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    ArchiveUtils.copyEntry(input, output, overwrite);
                }
            }
        }

        return extractionRoot;
    }

    /**
     * Checks for path traversal and verifies that every requested file exists before extraction.
     *
     * @param selected requested file names, or empty for full extraction
     * @throws IOException if the archive cannot be opened or read
     * @throws ArchiveException if path traversal is found or a requested file is missing
     */
    private void validateForExtraction(Set<String> selected) throws IOException, ArchiveException {
        final Set<String> missing = new LinkedHashSet<>(selected);
        final Path extractionRoot = archive.toAbsolutePath().normalize().getParent();

        try (InputStream source = Files.newInputStream(archive);
                var input = opener.open(source)) {
            ArchiveEntry entry;

            while ((entry = input.getNextEntry()) != null) {
                if (ArchiveUtils.isUnsafeEntry(extractionRoot, entry.getName())) {
                    throw new ArchiveException("Invalid Archive Entry; contains path traversal: " + entry.getName());
                }
                if (!entry.isDirectory()) {
                    missing.remove(entry.getName());
                }
            }
        }

        if (!missing.isEmpty()) {
            throw new ArchiveException("Files not found in archive: " + String.join(", ", missing));
        }
    }

    /**
     * Finds the first entry containing path traversal.
     *
     * @return the first offending entry name, or {@code null} when none is found
     * @throws IOException if the archive cannot be opened or read
     */
    private String findUnsafeEntry() throws IOException {

        final Path extractionRoot = archive.toAbsolutePath().normalize().getParent();

        try (InputStream source = Files.newInputStream(archive);
                var input = opener.open(source)) {
            ArchiveEntry entry;

            while ((entry = input.getNextEntry()) != null) {
                if (ArchiveUtils.isUnsafeEntry(extractionRoot, entry.getName())) {
                    return entry.getName();
                }
            }
        }

        return null;
    }
}