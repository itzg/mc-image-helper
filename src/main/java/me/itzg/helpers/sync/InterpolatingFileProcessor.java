package me.itzg.helpers.sync;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.env.Interpolator;

@Slf4j
class InterpolatingFileProcessor implements FileProcessor {
    private final ReplaceEnvOptions replaceEnv;
    private final Interpolator interpolator;
    private final FileProcessor fallbackProcessor;

    public InterpolatingFileProcessor(ReplaceEnvOptions replaceEnv, Interpolator interpolator, FileProcessor fallbackProcessor) {
        this.replaceEnv = replaceEnv;
        this.interpolator = interpolator;
        this.fallbackProcessor = fallbackProcessor;
    }

    @Override
    public void processFile(Path srcFile, Path destFile) throws IOException {
        if (replaceEnv.matches(destFile)) {
            log.info("Interpolating {} -> {}", srcFile, destFile);

            final byte[] content = Files.readAllBytes(srcFile);

            final Interpolator.Result<byte[]> result;
            try {
                result = interpolator.interpolate(content);
            } catch (Exception e) {
                log.warn("Failed to interpolate {}, using copy instead: {}", srcFile, e.getMessage());
                log.debug("Details", e);
                fallbackProcessor.processFile(srcFile, destFile);
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
            fallbackProcessor.processFile(srcFile, destFile);
        }
    }
}
