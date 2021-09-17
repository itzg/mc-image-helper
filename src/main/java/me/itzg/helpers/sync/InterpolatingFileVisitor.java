package me.itzg.helpers.sync;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
class InterpolatingFileVisitor extends SynchronizingFileVisitor {
    private final ReplaceEnvOptions replaceEnv;
    private final Interpolator interpolator;

    public InterpolatingFileVisitor(Path src, Path dest, boolean skipNewerInDestination, ReplaceEnvOptions replaceEnv, Interpolator interpolator) {
        super(src, dest, skipNewerInDestination);
        this.replaceEnv = replaceEnv;
        this.interpolator = interpolator;
    }

    @Override
    protected void processFile(Path srcFile, Path destFile) throws IOException {
        if (replaceEnv.matches(destFile)) {
            log.info("Interpolating {} -> {}", srcFile, destFile);

            final byte[] content = Files.readAllBytes(srcFile);

            final Interpolator.Result<byte[]> result;
            try {
                result = interpolator.interpolate(content);
            } catch (IOException e) {
                log.warn("Failed to interpolate {}, using copy instead: {}", srcFile, e.getMessage());
                log.debug("Details", e);
                copyFile(srcFile, destFile);
                return;
            }
            if (result.getReplacementCount() > 0) {
                log.debug("Replaced {} variable(s) in {}", result.getReplacementCount(), destFile);
            }
            try (OutputStream out = Files.newOutputStream(destFile)) {
                out.write(result.getContent());
            }
            Files.setLastModifiedTime(destFile, Files.getLastModifiedTime(srcFile));

        } else {
            super.processFile(srcFile, destFile);
        }
    }
}
