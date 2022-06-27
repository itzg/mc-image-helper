package me.itzg.helpers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lombok.Getter;
import me.itzg.helpers.assertcmd.AssertCommand;
import me.itzg.helpers.errors.ExceptionHandler;
import me.itzg.helpers.errors.ExitCodeMapper;
import me.itzg.helpers.get.GetCommand;
import me.itzg.helpers.modrinth.ModrinthCommand;
import me.itzg.helpers.patch.PatchCommand;
import me.itzg.helpers.singles.Asciify;
import me.itzg.helpers.singles.HashCommand;
import me.itzg.helpers.singles.YamlPathCmd;
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
        AssertCommand.class,
        CompareVersionsCommand.class,
        GetCommand.class,
        HashCommand.class,
        InterpolateCommand.class,
        JavaReleaseCommand.class,
        ModrinthCommand.class,
        PatchCommand.class,
        Sync.class,
        SyncAndInterpolate.class,
        YamlPathCmd.class,
    }
)
public class McImageHelper {

  @SuppressWarnings("unused")
  @CommandLine.Option(names = {"-h",
      "--help"}, usageHelp = true, description = "Show this usage and exit")
  boolean showHelp;

  @SuppressWarnings("unused")
  @Option(names = "--debug", description = "Enable debug output. Can also set environment variable DEBUG_HELPER",
      defaultValue = "${env:DEBUG_HELPER}")
  void setDebug(boolean value) {
    ((Logger) LoggerFactory.getLogger("me.itzg.helpers")).setLevel(
        value ? Level.DEBUG : Level.INFO);
  }

  @Option(names = {"-s", "--silent"}, description = "Don't output logs even if there's an error")
  @Getter
  boolean silent;

  public static void main(String[] args) {
    final McImageHelper rootCommand = new McImageHelper();
    System.exit(
        new CommandLine(rootCommand)
            .setExitCodeExceptionMapper(new ExitCodeMapper())
            .setExecutionExceptionHandler(new ExceptionHandler(rootCommand))
            .execute(args)
    );
  }

}
