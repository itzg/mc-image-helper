package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Zip {

    public static boolean validate(Path zip) throws IOException, SecurityException {
        final Path extractionRoot = zip.toAbsolutePath().normalize().getParent();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                final Path output = extractionRoot.resolve(entry.getName()).normalize();

                if (!output.startsWith(extractionRoot)) {
                    log.warn("Zip slip detected at: " + entry.getName() + " in zip: " + zip.toAbsolutePath());
                    return false;
                }
            }
        }

        return true;
    }
}