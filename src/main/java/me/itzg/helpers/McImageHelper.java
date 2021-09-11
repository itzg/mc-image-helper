package me.itzg.helpers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import me.itzg.helpers.asciify.Asciify;
import me.itzg.helpers.sync.Sync;
import me.itzg.helpers.sync.SyncAndInterpolate;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "mc-image-helper",
        mixinStandardHelpOptions = true,
        versionProvider = MavenVersionProvider.class,
        subcommands = {
                SyncAndInterpolate.class,
                Sync.class,
                Asciify.class
        }
)
public class McImageHelper {
    @Option(names = "--debug", description = "Enable debug output")
    void setDebug(boolean value) {
        ((Logger) LoggerFactory.getLogger("me.itzg.helpers")).setLevel(value ? Level.DEBUG : Level.INFO);
    }

    public static void main(String[] args) {
        System.exit(
                new CommandLine(new McImageHelper()).execute(args)
        );
    }

}
