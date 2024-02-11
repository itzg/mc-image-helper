package me.itzg.helpers.github;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "download-latest-asset",
    description = "From the latest release, downloads the first matching asset, and outputs the downloaded filename"
)
@Slf4j
public class DownloadLatestAssetCommand implements Callable<Integer> {

    String organization;
    String repo;

    @Option(names = "--name-pattern")
    Pattern namePattern;

    @Option(names = "--output-directory", defaultValue = ".")
    Path outputDirectory;

    @Option(names = "--api-base-url", defaultValue = GithubClient.DEFAULT_API_BASE_URL)
    String apiBaseUrl;

    @Parameters(arity = "1", paramLabel = "org/repo")
    public void setOrgRepo(String input) {
        final String[] parts = input.split("/", 2);
        if (parts.length != 2) {
            throw new InvalidParameterException("org/repo needs to be slash delimited");
        }
        this.organization = parts[0];
        this.repo = parts[1];
    }

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws Exception {

        try (SharedFetch sharedFetch = Fetch.sharedFetch("github download-latest-asset", sharedFetchArgs.options())) {

            final GithubClient client = new GithubClient(sharedFetch, apiBaseUrl);
            final Path result = client.downloadLatestAsset(organization, repo, namePattern, outputDirectory)
                .block();

            if (result == null) {
                log.error("Unable to locate latest, matching asset from {}/{}", organization, repo);
                return CommandLine.ExitCode.USAGE;
            }
            else {
                System.out.println(result);
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
