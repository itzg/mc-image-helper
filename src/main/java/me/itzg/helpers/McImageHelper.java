package me.itzg.helpers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.assertcmd.AssertCommand;
import me.itzg.helpers.curseforge.InstallCurseForgeCommand;
import me.itzg.helpers.errors.ExceptionHandler;
import me.itzg.helpers.errors.ExitCodeMapper;
import me.itzg.helpers.fabric.InstallFabricLoaderCommand;
import me.itzg.helpers.find.FindCommand;
import me.itzg.helpers.forge.InstallForgeCommand;
import me.itzg.helpers.get.GetCommand;
import me.itzg.helpers.modrinth.ModrinthCommand;
import me.itzg.helpers.mvn.MavenDownloadCommand;
import me.itzg.helpers.patch.PatchCommand;
import me.itzg.helpers.singles.Asciify;
import me.itzg.helpers.singles.HashCommand;
import me.itzg.helpers.singles.YamlPathCmd;
import me.itzg.helpers.sync.InterpolateCommand;
import me.itzg.helpers.sync.Sync;
import me.itzg.helpers.sync.SyncAndInterpolate;
import me.itzg.helpers.vanillatweaks.VanillaTweaksCommand;
import me.itzg.helpers.versions.CompareVersionsCommand;
import me.itzg.helpers.versions.JavaReleaseCommand;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;

@Command(name = "mc-image-helper",
    versionProvider = McImageHelper.AppVersionProvider.class,
    subcommands = {
        Asciify.class,
        AssertCommand.class,
        CompareVersionsCommand.class,
        FindCommand.class,
        GetCommand.class,
        HashCommand.class,
        InstallCurseForgeCommand.class,
        InstallFabricLoaderCommand.class,
        InstallForgeCommand.class,
        InterpolateCommand.class,
        JavaReleaseCommand.class,
        MavenDownloadCommand.class,
        ModrinthCommand.class,
        PatchCommand.class,
        Sync.class,
        SyncAndInterpolate.class,
        YamlPathCmd.class,
        VanillaTweaksCommand.class,
    }
)
@Slf4j
public class McImageHelper {

  public static final String OPTION_SPLIT_COMMAS = "\\s*,\\s*";

  @SuppressWarnings("unused")
  @CommandLine.Option(names = {"-h",
      "--help"}, usageHelp = true, description = "Show this usage and exit")
  boolean showHelp;

  @Option(names = {"-V", "--version"}, versionHelp = true)
  boolean showVersion;

  @SuppressWarnings("unused")
  @Option(names = "--debug", description = "Enable debug output."
      + " Can also set environment variables DEBUG_HELPER or DEBUG",
      defaultValue = "${env:DEBUG_HELPER:-${env:DEBUG}}")
  void setDebug(boolean value) {
    ((Logger) LoggerFactory.getLogger("me.itzg.helpers")).setLevel(
        value ? Level.DEBUG : Level.INFO);
  }

  @Option(names = {"-s", "--silent"}, description = "Don't output logs even if there's an error")
  @Getter
  boolean silent;

  private static String version;

  public static void main(String[] args) {
    final McImageHelper rootCommand = new McImageHelper();
    try {
      version = McImageHelper.loadVersion();
    } catch (IOException e) {
      log.error("Failed to load version", e);
      System.exit(1);
    }

    System.exit(
        new CommandLine(rootCommand)
            .setExitCodeExceptionMapper(new ExitCodeMapper())
            .setExecutionExceptionHandler(new ExceptionHandler(rootCommand))
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args)
    );
  }

  private static String loadVersion() throws IOException {
    final Enumeration<URL> resources = McImageHelper.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
    while (resources.hasMoreElements()) {
      final URL url = resources.nextElement();
      try (InputStream inputStream = url.openStream()) {
        final Manifest manifest = new Manifest(inputStream);
        final Attributes attributes = manifest.getMainAttributes();
        if ("mc-image-helper".equals(attributes.getValue(Name.IMPLEMENTATION_TITLE))) {
          return attributes.getValue(Name.IMPLEMENTATION_VERSION);
        }
      }
    }
    return "???";
  }

  public static String getVersion() {
    return version;
  }

  public static class AppVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {

          return new String[]{
              "${COMMAND-FULL-NAME}",
              version
          };
    }
  }
}
