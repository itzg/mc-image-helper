package me.itzg.helpers.sync;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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

    @Parameters(arity = "2..*", description = "src... dest directories",
        split = McImageHelper.SPLIT_COMMA_NL, splitSynopsisLabel = McImageHelper.SPLIT_SYNOPSIS_COMMA_NL)
    List<Path> srcDest;

    @Override
    public Integer call() throws Exception {
        log.debug("Configured with {}", this);

        return SynchronizingFileVisitor.walkDirectories(srcDest, skipNewerInDestination, new CopyingFileProcessor());
    }
}
