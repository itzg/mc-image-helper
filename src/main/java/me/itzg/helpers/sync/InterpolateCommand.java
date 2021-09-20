package me.itzg.helpers.sync;

import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.env.StandardEnvironmentVariablesProvider;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "interpolate",
description = "Interpolates existing files in one or more directories")
@Slf4j
public class InterpolateCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    @ToString.Exclude
    boolean showHelp;

    @CommandLine.ArgGroup(multiplicity = "1", exclusive = false)
    ReplaceEnvOptions replaceEnv = new ReplaceEnvOptions();

    @CommandLine.Parameters(paramLabel = "DIRECTORY")
    List<Path> directories;

    @Override
    public Integer call() throws Exception {
        final Interpolator interpolator = new Interpolator(new StandardEnvironmentVariablesProvider(), replaceEnv.prefix);

        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                log.error("The given path is not a directory: {}", directory);
                return 1;
            }
            try {
                processDirectory(directory, interpolator);
            } catch (IOException e) {
                log.error("Failed to process directory={}: {}", directory, e.getMessage());
                log.debug("Details", e);
                return 2;
            }
        }

        return 0;
    }

    private void processDirectory(Path directory, Interpolator interpolator) throws IOException {
        Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(replaceEnv::matches)
                .forEach(path -> processFile(path, interpolator));
    }

    @SneakyThrows
    private void processFile(Path path, Interpolator interpolator) {
        log.debug("Interpolating file={}", path);
        final byte[] original = Files.readAllBytes(path);

        final Interpolator.Result<byte[]> result = interpolator.interpolate(original);
        if (result.getReplacementCount() > 0) {
            Files.write(path, result.getContent());
            log.info("Replaced {} variable(s) in {}", result.getReplacementCount(), path);
        }
    }
}
