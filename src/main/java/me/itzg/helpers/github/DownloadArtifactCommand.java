package me.itzg.helpers.github;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "download-artifact",
    description = "Download an artifact from a successful GitHub Actions workflow as received or unzipped")
@Slf4j
public class DownloadArtifactCommand implements Callable<Integer> {

    String organization;
    String repo;

    @Option(names = {"--help", "-h"}, usageHelp = true)
    public Boolean help;

    static class RunSelector {

        @Option(names = "--workflow", paramLabel = "ID|FILE",
            description = "Workflow ID or filename whose latest successful run should be used")
        private String workflow;

        @Option(names = "--run-id", paramLabel = "ID",
            description = "Specific workflow run ID to use")
        private Long runId;
    }

    @ArgGroup(multiplicity = "1")
    private RunSelector runSelector;

    @Option(names = {"--output-directory", "-o"}, defaultValue = ".")
    private Path outputDirectory;

    @Option(names = "--unzip", description = "Extract the downloaded response and remove it afterward")
    private boolean unzip;

    @Option(names = "--overwrite", defaultValue = "false",
        description = "When extracting zip, overwrite existing files")
    private boolean overwrite;

    @Option(names = "--name-pattern", required = true,
        description = "Regular expression that must match exactly one artifact name")
    private Pattern namePattern;

    @ParentCommand
    private GithubCommands parent;

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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'call'");
    }

}
