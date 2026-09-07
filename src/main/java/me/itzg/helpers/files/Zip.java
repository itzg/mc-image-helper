package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.ArchiveException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Zip {

    /**
     * Checks if a Zip contains a Zip Slip.
     *
     * @see <a href="https://security.snyk.io/research/zip-slip-vulnerability">Zip Slip</a>
     *
     * @param zip Path to the zip to check
     * @return Boolean if zip slip is found within zip
     * @throws IOException
     */
    public static boolean containsZipSlip(Path zip) throws IOException {
        final Path extractionRoot = zip.toAbsolutePath().normalize().getParent();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                final Path output = extractionRoot.resolve(entry.getName()).normalize();

                if (!output.startsWith(extractionRoot)) {
                    log.warn("Zip slip detected at: " + entry.getName() + " in zip: " + zip.toAbsolutePath());
                    return true;
                }
            }
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
    public static Path unzip(Path zip, Path destination, boolean overwrite) throws IOException, ArchiveException {
        final Path extractionRoot = destination.toAbsolutePath().normalize();

        Files.createDirectories(extractionRoot); // Creates directory, does not fail if directory already exists

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                final Path output = extractionRoot.resolve(entry.getName()).normalize();

                if (!output.startsWith(extractionRoot)) {
                    throw new ArchiveException("Invalid Zip Entry; contains zip slip: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());

                    if (overwrite) {
                        Files.copy(zis, output, StandardCopyOption.REPLACE_EXISTING);
                    } else if (Files.notExists(output)) {
                        Files.copy(zis, output);
                    }
                }
            }
        }

        return extractionRoot;

    }
}