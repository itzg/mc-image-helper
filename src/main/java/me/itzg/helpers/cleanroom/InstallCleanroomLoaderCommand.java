package me.itzg.helpers.cleanroom;

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
        String.join("|", CleanroomInstallerResolver.LATEST, VERSION_REGEX),
        Pattern.CASE_INSENSITIVE
    );

    static class VersionOrInstaller {

        @Spec
        CommandLine.Model.CommandSpec spec;

        String installerVersion;
        String cleanroomVersion;

        @Option(names = "--installer-version", required = true, defaultValue = CleanroomInstallerResolver.LATEST,
            description = "A specific Cleanroom installer version or to auto-resolve the version provide 'latest'."
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

        @Option(names = "--cleanroom-version", required = true, defaultValue = CleanroomInstallerResolver.LATEST,
            description = "A specific Cleanroom Loader version or to auto-resolve the version provide 'latest'."
                + " Default value is ${DEFAULT-VALUE}"
        )
        public void setCleanroomVersion(String cleanroomVersion) {
            if (!ALLOWED_VERSION.matcher(cleanroomVersion).matches()) {
                throw new ParameterException(spec.commandLine(),
                    "Invalid value for --cleanroom-version: " + cleanroomVersion
                );
            }
            this.cleanroomVersion = cleanroomVersion.toLowerCase();
        }

        @Option(names = "--cleanroom-installer", description = "Use a local cleanroom installer", paramLabel = "FILE")
        Path installer;
    }

    @ArgGroup
    InstallCleanroomLoaderCommand.VersionOrInstaller versionOrInstaller = new InstallCleanroomLoaderCommand.VersionOrInstaller();

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

    static class CleanroomUrlArgs {
        @Option(names = "--cleanroom_installer_releases-url", paramLabel = "URL",
            defaultValue = "${CLEANROOM_INSTALLER_RELEASE_URL:-" + CleanroomInstallerResolver.DEFAULT_RELEASE_URL + "}",
            description = "URL for Cleanroom installer JSON.%n"
                + "Can also be set via env var CLEANROOM_INSTALLER_RELEASE_URL%n"
                + "Default is ${DEFAULT-VALUE}"
        )
        String releaseUrl;

        public String getReleaseUrl() {
            return releaseUrl != null ? releaseUrl : CleanroomInstallerResolver.DEFAULT_RELEASE_URL;
        }
    }

    @ArgGroup(exclusive = false)
    CleanroomUrlArgs cleanroomUrlArgs = new CleanroomUrlArgs();

    @Override
    public Integer call() throws Exception {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-cleanroom", sharedFetchArgs.options())) {
            final CleanroomInstaller installer = new CleanroomInstaller(new CleanroomInstallerResolver(
                        sharedFetch, versionOrInstaller.installerVersion, versionOrInstaller.cleanroomVersion,
                        cleanroomUrlArgs.getReleaseUrl()
            ));

            installer.install(outputDirectory, resultsFile, forceReinstall);
        }

        return ExitCode.OK;
    }
}
