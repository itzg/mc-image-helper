package me.itzg.helpers.forge;

import picocli.CommandLine.Option;

public class ForgeUrlArgs {

    @Option(names = "--forge-promotions-url", paramLabel = "URL",
        defaultValue = "${FORGE_PROMOTIONS_URL:-" + ForgeInstallerResolver.DEFAULT_PROMOTIONS_URL + "}",
        description = "URL for Forge promotions JSON.%n"
            + "Can also be set via env var FORGE_PROMOTIONS_URL%n"
            + "Default is ${DEFAULT-VALUE}"
    )
    String promotionsUrl;

    public String getPromotionsUrl() {
        return promotionsUrl != null ? promotionsUrl : ForgeInstallerResolver.DEFAULT_PROMOTIONS_URL;
    }

    @Option(names = "--forge-maven-repo-url", paramLabel = "URL",
        defaultValue = "${FORGE_MAVEN_REPO_URL:-" + ForgeInstallerResolver.DEFAULT_MAVEN_REPO_URL + "}",
        description = "URL for Forge Maven repository where installer is downloaded.%n"
            + "Can also be set via env var FORGE_MAVEN_REPO_URL%n"
            + "Default is ${DEFAULT-VALUE}"
    )
    String mavenRepoUrl;

    public String getMavenRepoUrl() {
        return mavenRepoUrl != null ? mavenRepoUrl : ForgeInstallerResolver.DEFAULT_MAVEN_REPO_URL;
    }
}
