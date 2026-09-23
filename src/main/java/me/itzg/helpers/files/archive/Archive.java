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

@Slf4j
@AllArgsConstructor
public final class Archive {

    private final Path archive;
    private final ArchiveOpener opener;

    public boolean containsPathTraversal() throws IOException {
        final String unsafeEntry = findUnsafeEntry();

        if (unsafeEntry != null) {
            log.warn("Path traversal detected at: " + unsafeEntry + " in archive: " + archive.toAbsolutePath());
            return true;
        }

        return false;
    }

    public Path extract(Path destination, boolean overwrite) throws IOException, ArchiveException {
        return extract(destination, List.of(), overwrite);
    }

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