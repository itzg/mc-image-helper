package me.itzg.helpers.github;

import picocli.CommandLine.Command;

@Command(name = "github",
    subcommands = {
        DownloadLatestAssetCommand.class
    }
)
public class GithubCommands {

}
