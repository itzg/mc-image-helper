package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class TarArchive implements Archive {

    private final Path archive;

    TarArchive(Path archive) {
        this.archive = archive;
    }

    protected final Path archive() {
        return archive;
    }

    protected abstract InputStream openInputStream() throws IOException;

    @Override
    public boolean containsPathTraversal() throws IOException {
        final String unsafeEntry = findUnsafeEntry();

        if (unsafeEntry != null) {
            log.warn("Path traversal detected at: " + unsafeEntry + " in archive: " + archive.toAbsolutePath());
            return true;
        }

        return false;
    }

    @Override
    public Path extract(Path destination, boolean overwrite) throws IOException, ArchiveException {
        final String unsafeEntry = findUnsafeEntry();
        if (unsafeEntry != null) {
            throw new ArchiveException("Invalid Archive Entry; contains path traversal: " + unsafeEntry);
        }

        final Path extractionRoot = prepareDestination(destination);

        try (TarArchiveInputStream tar = new TarArchiveInputStream(openInputStream())) {
            TarArchiveEntry entry;

            while ((entry = tar.getNextEntry()) != null) {
                final Path output = resolveEntry(extractionRoot, entry.getName(),
                        "Invalid Archive Entry; contains path traversal: ");

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    copyEntry(tar, output, overwrite);
                }
            }
        }

        return extractionRoot;
    }

    private String findUnsafeEntry() throws IOException {
        final Path extractionRoot = archive.toAbsolutePath().normalize().getParent();

        try (TarArchiveInputStream tar = new TarArchiveInputStream(openInputStream())) {
            TarArchiveEntry entry;

            while ((entry = tar.getNextEntry()) != null) {
                if (isUnsafeEntry(extractionRoot, entry.getName())) {
                    return entry.getName();
                }
            }
        }

        return null;
    }
}
