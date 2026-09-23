package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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
        final String unsafeEntry = findUnsafeEntry();
        if (unsafeEntry != null) {
            throw new ArchiveException("Invalid Archive Entry; contains path traversal: " + unsafeEntry);
        }

        final Path extractionRoot = ArchiveUtils.prepareDestination(destination);

        try (InputStream source = Files.newInputStream(archive);
                var input = opener.open(source)) {
            ArchiveEntry entry;

            while ((entry = input.getNextEntry()) != null) {
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