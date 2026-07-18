package me.itzg.helpers.vanilla;

import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.versions.MinecraftVersionsApi;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "install-vanilla", description = "Downloads and installs a requested version of vanilla Minecraft")
public class InstallVanillaCommand implements Callable<Integer> {
    @SuppressWarnings("unused")
    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

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

    @Option(names = "--version", required = true, paramLabel = "VERSION",
    defaultValue = "latest",
    description = "the version of Minecraft to install; defaults to the latest release")
    String minecraftVersion;

    @Override
    public Integer call() throws Exception {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-vanilla", sharedFetchArgs.options())) {
            final VanillaInstaller installer = new VanillaInstaller(sharedFetch, new MinecraftVersionsApi(sharedFetch));
            installer.install(minecraftVersion, outputDirectory, resultsFile, forceReinstall);
        }
        return ExitCode.OK;
    }
}
