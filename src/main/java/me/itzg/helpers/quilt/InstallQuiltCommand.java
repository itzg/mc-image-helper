package me.itzg.helpers.quilt;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "install-quilt", description = "Installs Quilt mod loader")
public class InstallQuiltCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true)
    boolean showHelp;

    @Option(names = "--minecraft-version", defaultValue = "latest", required = true, paramLabel = "VERSION",
        description = "'latest', 'snapshot', or specific version"
    )
    String minecraftVersion;

    @Option(names = "--loader-version", paramLabel = "VERSION",
        description = "Default uses latest"
    )
    String loaderVersion;

    @ArgGroup
    Inputs inputs = new Inputs();

    static class Inputs {

        @Option(names = "--installer-url", paramLabel = "URL")
        URI installerUrl;

        @Option(names = "--installer-version", paramLabel = "VERSION",
            description = "Default uses latest"
        )
        String installerVersion;
    }

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Option(names = "--repo-url", defaultValue = QuiltInstaller.DEFAULT_REPO_URL,
        description = "Default: ${DEFAULT-VALUE}"
    )
    String repoUrl;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--force-reinstall", defaultValue = "${env:QUILT_FORCE_REINSTALL:-false}")
    boolean forceReinstall;

    @Override
    public Integer call() throws Exception {

        try (QuiltInstaller installer = new QuiltInstaller(
            repoUrl, sharedFetchArgs.options(), outputDirectory,
            minecraftVersion
        )
            .setResultsFile(resultsFile)
            .setForceReinstall(forceReinstall)
        ) {
            if (inputs.installerUrl != null) {
                installer.installFromUrl(inputs.installerUrl, loaderVersion);
            }
            else {
                installer.installWithVersion(
                    inputs.installerVersion,
                    loaderVersion
                );
            }
        }

        return ExitCode.OK;
    }
}
