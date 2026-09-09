package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.ArchiveException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class Zip implements Archive {

    private final Path zip;

    /**
     * Checks if a Zip contains a Zip Slip.
     *
     * @see <a href="https://security.snyk.io/research/zip-slip-vulnerability">Zip Slip</a>
     *
     * @param zip Path to the zip to check
     * @return Boolean if zip slip is found within zip
     * @throws IOException
     */
    @Override
    public boolean containsPathTraversal() throws IOException {
        final String unsafeEntry = findUnsafeEntry();

        if (unsafeEntry != null) {
            log.warn("Zip slip detected at: " + unsafeEntry + " in zip: " + zip.toAbsolutePath());
            return true;
        }

        return false;
    }

    /**
     * Extracts Zip file whilst checking for Zip Slips.
     *
     * @param zip         Path to the zip to extract
     * @param destination Destination to extract zip to
     * @param overwrite   Boolean flag to overrwite files when extracting
     * @return Path to extracted files
     * @throws IOException
     */
    @Override
    public Path extract(Path destination, boolean overwrite) throws IOException, ArchiveException {
        final String unsafeEntry = findUnsafeEntry();
        if (unsafeEntry != null) {
            throw new ArchiveException("Invalid Zip Entry; contains zip slip: " + unsafeEntry);
        }

        final Path extractionRoot = prepareDestination(destination);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                final Path output = resolveEntry(extractionRoot, entry.getName(),
                        "Invalid Zip Entry; contains zip slip: ");

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    copyEntry(zis, output, overwrite);
                }
            }
        }

        return extractionRoot;

    }

    private String findUnsafeEntry() throws IOException {
        final Path extractionRoot = zip.toAbsolutePath().normalize().getParent();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (isUnsafeEntry(extractionRoot, entry.getName())) {
                    return entry.getName();
                }
            }
        }

        return null;
    }
}
