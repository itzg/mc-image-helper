package me.itzg.helpers.forge;

import static me.itzg.helpers.McImageHelper.VERSION_REGEX;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "install-forge", description = "Downloads and installs a requested version of Forge")
public class InstallForgeCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

    public static final Pattern ALLOWED_MINECRAFT_VERSION = Pattern.compile(
        String.join("|", "latest", VERSION_REGEX),
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern ALLOWED_FORGE_VERSION = Pattern.compile(
        String.join("|", ForgeInstallerResolver.LATEST, ForgeInstallerResolver.RECOMMENDED, VERSION_REGEX),
        Pattern.CASE_INSENSITIVE
    );

    @Option(names = "--minecraft-version", required = true, paramLabel = "VERSION",
        defaultValue = "latest",
        description = "'latest', which is the default, or a specific version"
    )
    public void setMinecraftVersion(String minecraftVersion) {
        if (!ALLOWED_MINECRAFT_VERSION.matcher(minecraftVersion).matches()) {
            throw new ParameterException(spec.commandLine(), "Invalid value for minecraft version: " + minecraftVersion);
        }
        this.minecraftVersion = minecraftVersion;
    }

    String minecraftVersion;

    static class VersionOrInstaller {

        @Spec
        CommandLine.Model.CommandSpec spec;

        String version;

        @Option(names = "--forge-version", required = true, defaultValue = ForgeInstallerResolver.RECOMMENDED,
            description = "A specific Forge version or to auto-resolve the version provide 'latest' or 'recommended'."
                + " Default value is ${DEFAULT-VALUE}"
        )
        public void setVersion(String version) {
            if (!ALLOWED_FORGE_VERSION.matcher(version).matches()) {
                throw new ParameterException(spec.commandLine(),
                    "Invalid value for --forge-version: " + version
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

    @Option(names = "--results-file", description =
        "A key=value file suitable for scripted environment variables. Currently includes"
            + "\n  SERVER: the entry point jar or script", paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--force-reinstall")
    boolean forceReinstall;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws Exception {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-forge", sharedFetchArgs.options())) {

            final ForgeInstaller installer = new ForgeInstaller(
                versionOrInstaller.installer != null ?
                    new ProvidedInstallerResolver(versionOrInstaller.installer)
                    : new ForgeInstallerResolver(sharedFetch, minecraftVersion, versionOrInstaller.version)

            );

            installer.install(outputDirectory, resultsFile, forceReinstall, "Forge");
        }

        return ExitCode.OK;
    }
}
