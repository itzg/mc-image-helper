package me.itzg.helpers.sync;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.env.StandardEnvironmentVariablesProvider;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "sync-and-interpolate",
        description = "Synchronizes the contents of one directory to another with conditional variable interpolation.")
@ToString
@Slf4j
public class SyncAndInterpolate implements Callable<Integer> {
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    @ToString.Exclude
    boolean showHelp;

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
        log.debug("Configured with {}", this);

        try {
            Files.walkFileTree(src,
                    new SynchronizingFileVisitor(src, dest, skipNewerInDestination,
                            new InterpolatingFileProcessor(
                                    replaceEnv,
                                    new Interpolator(new StandardEnvironmentVariablesProvider(), replaceEnv.prefix),
                                    new CopyingFileProcessor()
                            )

                    )
            );
        } catch (IOException e) {
            log.error("Failed to sync and interpolate {} into {} : {}", src, dest, e.getMessage());
            log.debug("Details", e);
            return 1;
        }

        return 0;
    }

}
