package me.itzg.helpers.sync;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Slf4j
public class CopyingFileProcessor implements FileProcessor {
    @Override
    public void processFile(Path srcFile, Path destFile) throws IOException {
        log.info("Copying {} -> {}", srcFile, destFile);

        Files.copy(srcFile, destFile, COPY_ATTRIBUTES, REPLACE_EXISTING);
    }
}
