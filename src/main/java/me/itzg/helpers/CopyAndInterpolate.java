package me.itzg.helpers;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "copy-and-interpolate", mixinStandardHelpOptions = true,
        description = "Copies the contents of one directory to another with conditional variable interpolation.")
@ToString
@Slf4j
public class CopyAndInterpolate implements Callable<Integer> {
    @Option(names = "--skip-newer-in-destination",
            // this is same as rsync's --update option
            description = "Skip any files that exist in the destination and have a newer modification time than the source.")
    boolean skipNewerInDestination;

    @ArgGroup(multiplicity = "1", exclusive = false)
    ReplaceEnvOptions replaceEnv = new ReplaceEnvOptions();

    @Parameters(index = "0", description = "source directory")
    Path src;

    @Parameters(index = "1", description = "destination directory")
    Path dest;

    @Override
    public Integer call() throws Exception {
        log.debug("CopyAndInterpolate : {}", this);

        try {
            Files.walkFileTree(src, new InterpolatingFileVisitor(src, dest, skipNewerInDestination, replaceEnv,
                    new Interpolator(new StandardEnvironmentVariablesProvider(), replaceEnv.prefix)));
        } catch (IOException e) {
            log.error("Failed to copy and interpolate {} into {} : {}", src, dest, e.getMessage());
            log.debug("Details", e);
            return 1;
        }

        return 0;
    }

}
