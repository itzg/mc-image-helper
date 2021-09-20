package me.itzg.helpers.sync;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "sync",
        description = "Synchronizes the contents of one directory to another.")
@ToString
@Slf4j
public class Sync implements Callable<Integer> {
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    @ToString.Exclude
    boolean showHelp;

    @Option(names = "--skip-newer-in-destination",
            // this is same as rsync's --update option
            description = "Skip any files that exist in the destination and have a newer modification time than the source.")
    boolean skipNewerInDestination;

    /**
     * Allows for this to be command-line "compatible" with sync-and-interpolate subcommand.
     */
    @CommandLine.Unmatched
    @ToString.Exclude
    List<String> extra;

    @Parameters(index = "0", description = "source directory")
    Path src;

    @Parameters(index = "1", description = "destination directory")
    Path dest;

    @Override
    public Integer call() throws Exception {
        log.debug("Configured with {}", this);

        try {
            Files.walkFileTree(src, new SynchronizingFileVisitor(src, dest, skipNewerInDestination, new CopyingFileProcessor()));
        } catch (IOException e) {
            log.error("Failed to sync {} into {} : {}", src, dest, e.getMessage());
            log.debug("Details", e);
            return 1;
        }

        return 0;
    }
}
