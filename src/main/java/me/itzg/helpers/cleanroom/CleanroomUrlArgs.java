package me.itzg.helpers.cleanroom;

import picocli.CommandLine.Option;

public class CleanroomUrlArgs {
    @Option(names = "--releases-url", paramLabel = "URL",
        defaultValue = "${DEFAULT_RELEASES_URL:-" + InstallerResolver.DEFAULT_RELEASES_URL + "}",
        description = "URL for Cleanroom installer JSON.%n"
            + "Can also be set via env var FORGE_PROMOTIONS_URL%n"
            + "Default is ${DEFAULT-VALUE}"
    )
    String promotionsUrl;

    public String getPromotionsUrl() {
        return promotionsUrl != null ? promotionsUrl : InstallerResolver.DEFAULT_RELEASES_URL;
    }
}
