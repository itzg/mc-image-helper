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

@Command(name = "install-neoforge", description = "Downloads and installs a requested version of NeoForge")
public class InstallNeoForgeCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

    public static final Pattern ALLOWED_MINECRAFT_VERSION = Pattern.compile(
        String.join("|", "latest", VERSION_REGEX),
        Pattern.CASE_INSENSITIVE
    );

    public static final Pattern ALLOWED_FORGE_VERSION = Pattern.compile(
        String.join("|", "latest", "beta", VERSION_REGEX),
        Pattern.CASE_INSENSITIVE
    );

    @Option(names = "--minecraft-version", required = true, paramLabel = "VERSION",
        defaultValue = "latest",
        description = "'latest', which is the default, or a specific version to narrow NeoForge version selection"
    )
    public void setMinecraftVersion(String minecraftVersion) {
        if (!ALLOWED_MINECRAFT_VERSION.matcher(minecraftVersion).matches()) {
            throw new ParameterException(spec.commandLine(), "Invalid value for minecraft version: " + minecraftVersion);
        }
        this.minecraftVersion = minecraftVersion;
    }

    String minecraftVersion;

    @Option(names = "--neoforge-version", required = true, defaultValue = "latest",
        description = "A specific NeoForge version, 'latest', or 'beta'."
            + " Default value is ${DEFAULT-VALUE}"
    )
    public void setVersion(String version) {
        if (!ALLOWED_FORGE_VERSION.matcher(version).matches()) {
            throw new ParameterException(spec.commandLine(),
                "Invalid value for --forge-version: " + version
            );
        }
        this.neoForgeVersion = version.toLowerCase();
    }

    private String neoForgeVersion;

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
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-neoforge", sharedFetchArgs.options())) {

            new ForgeInstaller(
                new NeoForgeInstallerResolver(sharedFetch, minecraftVersion, neoForgeVersion)
            )
                .install(outputDirectory, resultsFile, forceReinstall, "NeoForge");
        }

        return ExitCode.OK;
    }
}
