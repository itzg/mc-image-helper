package me.itzg.helpers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "mc-image-helper",
        subcommands = {
                CopyAndInterpolate.class
        }
)
public class McImageHelper {
    @Option(names = "--debug")
    void setDebug(boolean value) {
        ((Logger) LoggerFactory.getLogger("me.itzg.helpers")).setLevel(value ? Level.DEBUG : Level.INFO);
    }

    public static void main(String[] args) {
        System.exit(
                new CommandLine(new McImageHelper()).execute(args)
        );
    }

}
