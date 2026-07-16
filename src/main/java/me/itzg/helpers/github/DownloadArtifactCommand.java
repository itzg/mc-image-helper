package me.itzg.helpers.github;

import java.nio.file.Path;
import java.security.InvalidParameterException;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ArgGroup;

@Command(name = "Download workflow artifact", description = "Download an artifact from a successfull github action workflow, output as raw file from github, or unzip")
@Slf4j
public class DownloadArtifactCommand implements Callable<Integer> {

    String organisation;
    String repo;

    @Option(names = "--workflow")
    private String workflowName;

    @Option(names = "--run-id")
    private String workflowRunId;

    @Option(names = { "--output-directory", "-o" }, defaultValue = ".")
    private Path outputDirectory;

    @Option(names = "--unzip", defaultValue = "true")
    private Boolean unzip;

    @Option(names = "--name-pattern", description = "Name pattern of artifact to download")
    private String namePattern;

    @Parameters(arity = "1", paramLabel = "org/repo")
    public void setOrgRepo(String input) {
        final String[] parts = input.split("/", 2);
        if (parts.length != 2) {
            throw new InvalidParameterException("org/repo needs to be slash delimited");
        }
        this.organisation = parts[0];
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