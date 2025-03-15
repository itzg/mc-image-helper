package me.itzg.helpers.sync;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class SynchronizingFileVisitor implements FileVisitor<Path> {
    private final Path src;
    private final Path dest;
    private final boolean skipNewerInDestination;
    private final FileProcessor fileProcessor;

    public SynchronizingFileVisitor(Path src, Path dest, boolean skipNewerInDestination, FileProcessor fileProcessor) {
        this.src = src;
        this.dest = dest;
        this.skipNewerInDestination = skipNewerInDestination;
        this.fileProcessor = fileProcessor;
    }

    static int walkDirectories(List<Path> srcDest, boolean skipNewerInDestination,
        FileProcessor fileProcessor
    ) {
        final Path dest = srcDest.getLast();

        for (final Path src : srcDest.subList(0, srcDest.size() - 1)) {
            try {
                Files.walkFileTree(src, new SynchronizingFileVisitor(src, dest, skipNewerInDestination, fileProcessor));
            } catch (IOException e) {
                log.error("Failed to sync and interpolate {} into {} : {}", src, dest, e.getMessage());
                log.debug("Details", e);
                return 1;
            }
        }

        return 0;
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

        if (shouldProcessFile(srcFile, destFile)) {
            fileProcessor.processFile(srcFile, destFile);
        }
        else {
            log.debug("Skipping destFile={}", destFile);
        }

        return FileVisitResult.CONTINUE;
    }

    private boolean shouldProcessFile(Path srcFile, Path destFile) throws IOException {
        if (Files.notExists(destFile)) {
            return true;
        }

        final FileTime srcTime = Files.getLastModifiedTime(srcFile);
        final FileTime destTime = Files.getLastModifiedTime(destFile);

        if (skipNewerInDestination) {
            if (destTime.compareTo(srcTime) > 0) {
                log.debug("Skipping since dest={} is newer than src={}", destFile, srcFile);
                return false;
            }
        }

        final long srcSize = Files.size(srcFile);
        final long destSize = Files.size(destFile);

        log.debug("Comparing {} (size={}, time={}) to {} (size={}, time={})",
                srcFile, srcSize, srcTime,
                destFile, destSize, destTime);

        return srcSize != destSize ||
                // Use millisecond resolution since finer resolution became inconsistent with copied files
                // such as 2021-05-01T18:29:50.2805676Z vs 2021-05-01T18:29:50.280567Z
                srcTime.toMillis() != destTime.toMillis();
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException e) {
        log.warn("Failed to visit file {} due to {}:{}", file, e.getClass(), e.getMessage());
        log.debug("Details", e);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return FileVisitResult.CONTINUE;
    }
}
