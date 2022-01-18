package me.itzg.helpers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import me.itzg.helpers.asciify.Asciify;
import me.itzg.helpers.get.GetCommand;
import me.itzg.helpers.hash.HashCommand;
import me.itzg.helpers.patch.PatchCommand;
import me.itzg.helpers.sync.InterpolateCommand;
import me.itzg.helpers.sync.Sync;
import me.itzg.helpers.sync.SyncAndInterpolate;
import me.itzg.helpers.versions.CompareVersionsCommand;
import me.itzg.helpers.versions.JavaReleaseCommand;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "mc-image-helper",
    subcommands = {
        Asciify.class,
        CompareVersionsCommand.class,
        GetCommand.class,
        HashCommand.class,
        InterpolateCommand.class,
        JavaReleaseCommand.class,
        PatchCommand.class,
        Sync.class,
        SyncAndInterpolate.class,
    }
)
public class McImageHelper {
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    boolean showHelp;

    @Option(names = "--debug", description = "Enable debug output")
    void setDebug(boolean value) {
        ((Logger) LoggerFactory.getLogger("me.itzg.helpers")).setLevel(value ? Level.DEBUG : Level.INFO);
    }

    public static void main(String[] args) {
        System.exit(
                new CommandLine(new McImageHelper())
                    .execute(args)
        );
    }

}
