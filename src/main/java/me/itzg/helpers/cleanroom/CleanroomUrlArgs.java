package me.itzg.helpers.cleanroom;

import picocli.CommandLine.Option;

public class CleanroomUrlArgs {
    @Option(names = "--cleanroom_installer_releases-url", paramLabel = "URL",
        defaultValue = "${CLEANROOM_INSTALLER_RELEASE_URL:-" + InstallerResolver.DEFAULT_RELEASES_URL + "}",
        description = "URL for Cleanroom installer JSON.%n"
            + "Can also be set via env var CLEANROOM_INSTALLER_RELEASE_URL%n"
            + "Default is ${DEFAULT-VALUE}"
    )
    String releaseUrl;

    public String getReleaseUrl() {
        return releaseUrl != null ? releaseUrl : InstallerResolver.DEFAULT_RELEASES_URL;
    }
}
