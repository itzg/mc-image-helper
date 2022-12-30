package me.itzg.helpers.fabric;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.ResultsFileWriter;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "install-fabric-loader",
    description = "Provides a few ways to obtain a Fabric loader with simple cleanup of previous loader instances"
)
@Slf4j
public class InstallFabricLoaderCommand implements Callable<Integer> {

    @Option(names = {"--help","-h"}, usageHelp = true)
    boolean help;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @ArgGroup(multiplicity = "1")
    OriginOptions originOptions;

    static class OriginOptions {
        @ArgGroup(exclusive = false)
        VersionOptions versionOptions;

        @Option(names = "--from-local-file", paramLabel = "FILE")
        Path launcherFile;

        @Option(names = "--from-url", paramLabel = "URL")
        URI fromUri;
    }

    static class VersionOptions {
        @Option(names = "--minecraft-version", required = true, paramLabel = "VERSION")
        String minecraftVersion;

        @Option(names = "--installer-version", paramLabel = "VERSION",
            description = "By default the latest installer version is used"
        )
        String installerVersion;

        @Option(names = "--loader-version", paramLabel = "VERSION",
            description = "By default the latest launcher version is used")
        String loaderVersion;
    }

    @Override
    public Integer call() throws Exception {
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(outputDirectory, resultsFile);
        final Path launcher;
        if (originOptions.versionOptions != null) {
            launcher = installer.installUsingVersions(
                originOptions.versionOptions.minecraftVersion,
                originOptions.versionOptions.loaderVersion,
                originOptions.versionOptions.installerVersion
            );
        }
        else if (originOptions.fromUri != null) {
            launcher = installer.installUsingUri(originOptions.fromUri);
        }
        else {
            installer.installGivenLauncherFile(originOptions.launcherFile);
            launcher = originOptions.launcherFile;
        }

        log.debug("Fabric launcher installed/reused at {}", launcher);

        return ExitCode.OK;
    }
}
