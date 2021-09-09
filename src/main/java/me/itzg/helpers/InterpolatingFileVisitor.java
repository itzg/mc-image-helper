package me.itzg.helpers;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Slf4j
class InterpolatingFileVisitor implements FileVisitor<Path> {
    private final Path src;
    private final Path dest;
    private final boolean skipNewerInDestination;
    private final ReplaceEnvOptions replaceEnv;
    private final Interpolator interpolator;

    public InterpolatingFileVisitor(Path src, Path dest, boolean skipNewerInDestination, ReplaceEnvOptions replaceEnv, Interpolator interpolator) {
        this.src = src;
        this.dest = dest;
        this.skipNewerInDestination = skipNewerInDestination;
        this.replaceEnv = replaceEnv;
        this.interpolator = interpolator;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        log.trace("pre visit dir={}", dir);
        final Path destPath = dest.resolve(src.relativize(dir));

        log.debug("ensuring destinationDirectory={}", destPath);
        Files.createDirectories(destPath);

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path srcFile, BasicFileAttributes attrs) throws IOException {
        log.trace("visit file={}", srcFile);
        final Path destFile = dest.resolve(src.relativize(srcFile));

        if (processFile(srcFile, destFile)) {
            if (replaceEnv.matches(destFile)) {
                log.debug("interpolate from={} to={}", srcFile, destFile);

                try (BufferedReader srcReader = Files.newBufferedReader(srcFile)) {
                    try (BufferedWriter destWriter = Files.newBufferedWriter(destFile, StandardOpenOption.CREATE)) {
                        interpolator.interpolate(srcReader, destWriter);
                    }
                }
            } else {
                log.debug("copy from={} to={}", srcFile, destFile);

                Files.copy(srcFile, destFile, COPY_ATTRIBUTES, REPLACE_EXISTING);
            }
        }
        else {
            log.debug("skipping destFile={}", destFile);
        }

        return FileVisitResult.CONTINUE;
    }

    private boolean processFile(Path srcFile, Path destFile) throws IOException {
        if (Files.notExists(destFile)) {
            return true;
        }

        if (skipNewerInDestination) {
            if (Files.getLastModifiedTime(destFile).compareTo(Files.getLastModifiedTime(srcFile)) > 0) {
                return false;
            }
        }

        return Files.size(srcFile) != Files.size(destFile) ||
                Files.getLastModifiedTime(srcFile) != Files.getLastModifiedTime(destFile);
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        log.warn("Failed to access {} due to {}", file, exc.getMessage());
        log.debug("Details", exc);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
    }
}
