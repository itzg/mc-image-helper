package me.itzg.helpers.quilt;

import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "install-quilt", description = "Installs Quilt mod loader")
public class InstallQuiltCommand implements Callable<Integer> {
    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Option(names = "--repo-url", defaultValue = QuiltInstaller.DEFAULT_REPO_URL,
        description = "Default: ${DEFAULT-VALUE}"
    )
    String repoUrl;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--minecraft-version", required = true, paramLabel = "VERSION")
    String minecraftVersion;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--installer-version", paramLabel = "VERSION",
        description = "Default uses latest"
    )
    String installerVersion;

    @Option(names = "--loader-version", paramLabel = "VERSION",
        description = "Default uses latest"
    )
    String loaderVersion;

    @Option(names = "--force-reinstall")
    boolean forceReinstall;

    @Override
    public Integer call() throws Exception {

        new QuiltInstaller(repoUrl, sharedFetchArgs.options(), outputDirectory, minecraftVersion)
            .setInstallerVersion(installerVersion)
            .setLoaderVersion(loaderVersion)
            .setResultsFile(resultsFile)
            .setForceReinstall(forceReinstall)
            .install();

        return ExitCode.OK;
    }
}
