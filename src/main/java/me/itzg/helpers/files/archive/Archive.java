package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
     * Extracts the contents of the given directories of the archive, or everything, with each directory's
     * prefix removed from the output paths. Checks all entries for path traversal before writing any files.
     *
     * @param destination output directory
     * @param directories slash-separated directories within the archive, without leading or trailing slashes,
     *                    that don't overlap; an empty list extracts everything
     * @param replaceExisting decides whether an entry replaces an existing file; otherwise the file is skipped
     * @return the output paths of the file entries selected, split into written and skipped existing files
     * @throws IOException if reading or extracting the archive fails
     * @throws ArchiveException if an entry contains path traversal or a directory is not in the archive
     */
    public ExtractedFiles extractDirectories(Path destination, List<String> directories,
            ReplaceExisting replaceExisting) throws IOException, ArchiveException {
        final List<String> prefixes = directories.isEmpty() ? List.of("")
                : directories.stream().map(directory -> directory + "/").toList();
        validateDirectories(destination.toAbsolutePath().normalize(), prefixes);

        final Path extractionRoot = ArchiveUtils.prepareDestination(destination);
        final List<Path> written = new ArrayList<>();
        final List<Path> skipped = new ArrayList<>();

        try (InputStream source = Files.newInputStream(archive);
                var input = opener.open(source)) {
            ArchiveEntry entry;

            while ((entry = input.getNextEntry()) != null) {
                final String name = stripCurrentDirectory(entry.getName());
                final String prefix = findPrefix(prefixes, name);
                if (prefix == null) {
                    continue;
                }

                final String relative = name.substring(prefix.length());
                if (relative.isEmpty()) {
                    continue;
                }
                final Path output = ArchiveUtils.resolveEntry(extractionRoot, relative,
                        "Invalid Archive Entry; contains path traversal: ");

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else if (Files.exists(output) && !replaceExisting.test(entry, output)) {
                    skipped.add(output);
                } else {
                    ArchiveUtils.copyEntry(input, output, true);
                    written.add(output);
                }
            }
        }

        return new ExtractedFiles(written, skipped);
    }

    private static String findPrefix(List<String> prefixes, String name) {
        return prefixes.stream()
                .filter(name::startsWith)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks for path traversal against the extraction root, including the output paths with prefixes removed,
     * and verifies that every requested directory exists before extraction.
     *
     * @param extractionRoot absolute, normalized destination directory
     * @param prefixes requested directories, each with a trailing slash, or only an empty prefix for everything
     * @throws IOException if the archive cannot be opened or read
     * @throws ArchiveException if path traversal is found or a requested directory is missing
     */
    private void validateDirectories(Path extractionRoot, List<String> prefixes)
            throws IOException, ArchiveException {
        final Set<String> missing = new LinkedHashSet<>(prefixes);
        missing.remove("");

        try (InputStream source = Files.newInputStream(archive);
                var input = opener.open(source)) {
            ArchiveEntry entry;

            while ((entry = input.getNextEntry()) != null) {
                final String name = stripCurrentDirectory(entry.getName());
                final String prefix = findPrefix(prefixes, name);
                if (ArchiveUtils.isUnsafeEntry(extractionRoot, name)
                        || (prefix != null && ArchiveUtils.isUnsafeEntry(extractionRoot, name.substring(prefix.length())))) {
                    throw new ArchiveException("Invalid Archive Entry; contains path traversal: " + entry.getName());
                }
                if (prefix != null) {
                    missing.remove(prefix);
                }
            }
        }

        if (!missing.isEmpty()) {
            throw new ArchiveException("Directories not found in archive: " + missing.stream()
                    .map(prefix -> prefix.substring(0, prefix.length() - 1))
                    .collect(Collectors.joining(", ")));
        }
    }

    /**
     * Decides whether an archive entry replaces a file that already exists at its output path.
     */
    @FunctionalInterface
    public interface ReplaceExisting {
        boolean test(ArchiveEntry entry, Path existing) throws IOException;
    }

    /**
     * @param written absolute, normalized paths of the files written
     * @param skipped absolute, normalized paths of the existing files that were skipped
     */
    public record ExtractedFiles(List<Path> written, List<Path> skipped) {
    }

    /**
     * Removes leading {@code ./}, such as from {@code tar -c .}, so entries match a directory prefix.
     */
    private static String stripCurrentDirectory(String name) {
        String result = name;
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        return result;
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