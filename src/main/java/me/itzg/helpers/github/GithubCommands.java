package me.itzg.helpers.github;

import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "github",
    subcommands = {
        DownloadLatestAssetCommand.class
    }
)
@Getter
public class GithubCommands {

    @Option(names = "--api-base-url", defaultValue = GithubClient.DEFAULT_API_BASE_URL)
    String apiBaseUrl;

    @Option(names = "--token", defaultValue = "${env:GH_TOKEN}",
        description = "An access token for GitHub to elevate rate limit vs anonymous access"
    )
    String token;
}
