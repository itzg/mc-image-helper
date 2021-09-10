package me.itzg.helpers;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

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
                log.info("Interpolating {} -> {}", srcFile, destFile);

                final byte[] content = Files.readAllBytes(srcFile);

                final byte[] result = interpolator.interpolate(content);
                try (OutputStream out = Files.newOutputStream(destFile, StandardOpenOption.CREATE)) {
                    out.write(result);
                }
                Files.setLastModifiedTime(destFile, Files.getLastModifiedTime(srcFile));

            } else {
                log.info("Copying {} -> {}", srcFile, destFile);

                Files.copy(srcFile, destFile, COPY_ATTRIBUTES, REPLACE_EXISTING);
            }
        }
        else {
            log.debug("Skipping destFile={}", destFile);
        }

        return FileVisitResult.CONTINUE;
    }

    private boolean processFile(Path srcFile, Path destFile) throws IOException {
        if (Files.notExists(destFile)) {
            return true;
        }

        final FileTime srcTime = Files.getLastModifiedTime(srcFile);
        final FileTime destTime = Files.getLastModifiedTime(destFile);

        if (skipNewerInDestination) {
            if (destTime.compareTo(srcTime) > 0) {
                return false;
            }
        }

        final long srcSize = Files.size(srcFile);
        final long destSize = Files.size(destFile);

        log.trace("Comparing {} (size={}, time={}) to {} (size={}, time={})",
                srcFile, srcSize, srcTime,
                destFile, destSize, destTime);

        return srcSize != destSize ||
                !srcTime.equals(destTime);
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        log.warn("Failed to access {} due to {}", file, exc.getMessage());
        log.debug("Details", exc);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return FileVisitResult.CONTINUE;
    }
}
