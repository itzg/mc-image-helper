package me.itzg.helpers.files;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ZipTest {

    @TempDir
    Path tempDir;
    
    @Test
    void rejectsPathTraversalOutOfExtractionRoot() throws IOException {
        File slip = tempDir.resolve("slip.zip").toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(slip))) {
            ZipEntry entry = new ZipEntry("../../../../danger.txt");
            zos.putNextEntry(entry);
            zos.write("exploit".getBytes());
            zos.close();
        }

        assertThrows(SecurityException.class, () -> {
            Zip.validate(slip.toPath());
        });
    }


}