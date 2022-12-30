package me.itzg.helpers.forge;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "install-forge", description = "Downloads and installs a requested version of Forge")
public class InstallForgeCommand implements Callable<Integer> {
    @Option(names = {"--help","-h"}, usageHelp = true)
    boolean help;

    public static final Pattern ALLOWED_FORGE_VERSION = Pattern.compile(
        String.join("|", ForgeInstaller.LATEST, ForgeInstaller.RECOMMENDED, "\\d+(\\.\\d+)+"),
        Pattern.CASE_INSENSITIVE
    );

    @Option(names = "--minecraft-version", required = true)
    String minecraftVersion;

    static class VersionOrInstaller {
        @Spec
        CommandLine.Model.CommandSpec spec;

        String version;
        @Option(names = "--forge-version", required = true, defaultValue = ForgeInstaller.RECOMMENDED,
            description = "A specific Forge version or to auto-resolve the version provide 'latest' or 'recommended'."
                + " Default value is ${DEFAULT-VALUE}"
        )
        public void setVersion(String version) {
            if (!ALLOWED_FORGE_VERSION.matcher(version).matches()) {
                throw new ParameterException(spec.commandLine(),
                    "Invalid value for --forge-version: "+ version
                );
            }
            this.version = version.toLowerCase();
        }

        @Option(names = "--forge-installer", description = "Use a local forge installer")
        Path installer;
    }

    @ArgGroup
    VersionOrInstaller versionOrInstaller = new VersionOrInstaller();

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = "A key=value file suitable for scripted environment variables. Currently includes"
        + "\n  SERVER: the entry point jar or script", paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--force-reinstall")
    boolean forceReinstall;

    @Override
    public Integer call() throws Exception {
        final ForgeInstaller installer = new ForgeInstaller();
        installer.install(minecraftVersion, versionOrInstaller.version, outputDirectory, resultsFile, forceReinstall, versionOrInstaller.installer);

        return ExitCode.OK;
    }
}
