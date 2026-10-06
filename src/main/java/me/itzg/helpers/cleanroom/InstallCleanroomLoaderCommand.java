package me.itzg.helpers.cleanroom;

import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static me.itzg.helpers.McImageHelper.VERSION_REGEX;

@Command(name = "install-cleanroom", description = "Downloads and installs a requested version of Cleanroom")
public class InstallCleanroomLoaderCommand implements Callable<Integer> {

    @SuppressWarnings("unused")
    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

    public static final Pattern ALLOWED_VERSION = Pattern.compile(
        String.join("|", CleanroomInstaller.LATEST, VERSION_REGEX, "(\\d+[\\.\\d+]+)-(\\w+)"),
        Pattern.CASE_INSENSITIVE
    );

    static class Version {

        @Spec
        CommandLine.Model.CommandSpec spec;

        String installerVersion;
        String loaderVersion;

        @Option(names = "--installer-version", required = true, defaultValue = CleanroomInstaller.LATEST,
            description = "A specific Cleanroom installer version or to auto-resolve the version provide 'latest'.%n"
                + "Ignored if valid a local/remote (included legacy) is provided."
                + " Default value is ${DEFAULT-VALUE}"
        )
        public void setInstallerVersion(String installerVersion) {
            if (!ALLOWED_VERSION.matcher(installerVersion).matches()) {
                throw new ParameterException(spec.commandLine(),
                    "Invalid value for --installer-version: " + installerVersion
                );
            }
            this.installerVersion = installerVersion.toLowerCase();
        }

        @Option(names = "--loader-version", required = true, defaultValue = CleanroomInstaller.LATEST,
            description = "A specific Cleanroom Loader version or to auto-resolve by installer.%n"
                + "Ignored if only a valid local/remote legacy installer is provided."
                + " Default value is ${DEFAULT-VALUE}"
        )
        public void setLoaderVersion(String loaderVersion) {
            if (!ALLOWED_VERSION.matcher(loaderVersion).matches()) {
                throw new ParameterException(spec.commandLine(),
                    "Invalid value for --cleanroom-version: " + loaderVersion
                );
            }
            this.loaderVersion = loaderVersion.toLowerCase();
        }
    }

    static class Source {
        @Option(names = "--cleanroom-maven", paramLabel = "URL",
            defaultValue = "${CLEANROOM_MAVEN:-" + CleanroomManifest.DEFAULT_MAVEN_URL + "}",
            description = "URL for Cleanroom installer JSON.%n"
                + "Can also be set via env var CLEANROOM_MAVEN%n"
                + "Default is ${DEFAULT-VALUE}"
        )
        String mavenUrl;

        @Option(names = "--from-local-file", description = "Use a local installer, first entry before remote.",
            paramLabel = "FILE")
        Path local_file;

        @Option(names = "--from-url", description = "Use a remote installer.%n",
            paramLabel = "URL")
        URI remote_file;
    }

    @ArgGroup
    InstallCleanroomLoaderCommand.Version version = new InstallCleanroomLoaderCommand.Version();

    @ArgGroup
    InstallCleanroomLoaderCommand.Source source = new InstallCleanroomLoaderCommand.Source();

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
        final CleanroomInstaller installer = new CleanroomInstaller()
            .outputDirectory(outputDirectory)
            .resultsFile(resultsFile)
            .mavenUrl(source.mavenUrl)
            .sharedFetchOptions(sharedFetchArgs.options())
            .forceReinstall(forceReinstall)
            .installerVersion(version.installerVersion)
            .loaderVersion(version.loaderVersion);

        if (source.local_file != null)
            return installer.install(source.local_file) ? ExitCode.OK : ExitCode.SOFTWARE;

        if (source.remote_file != null)
            return installer.install(source.remote_file) ? ExitCode.OK : ExitCode.SOFTWARE;

        return installer.install() ? ExitCode.OK : ExitCode.SOFTWARE;
    }
}
