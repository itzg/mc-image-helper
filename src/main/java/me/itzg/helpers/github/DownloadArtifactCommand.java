package me.itzg.helpers.github;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.github.model.Artifact;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import reactor.core.publisher.Mono;

@Command(name = "download-artifact",
    description = "Download an artifact from a successful GitHub Actions workflow")
@Slf4j
public class DownloadArtifactCommand implements Callable<Integer> {

    String organization;
    String repo;

    @Option(names = {"--help", "-h"}, usageHelp = true)
    public Boolean help;

    static class RunSelector {

        @Option(names = "--workflow", paramLabel = "ID|FILE",
            description = "Query this workflow for the latest successful run to download an artifact")
        private String workflow;

        @Option(names = "--run-id", paramLabel = "ID",
            description = "Query this specific workflow run to download an artifact")
        private Long runId;
    }

    @ArgGroup(multiplicity = "1")
    private RunSelector runSelector;

    @Option(names = {"--output-directory", "-o"}, defaultValue = ".", description = "Output directory of downloaded artifact")
    private Path outputDirectory;

    @Option(names = "--unzip", description = "Extract the downloaded artifact")
    private boolean unzip;

    @Option(names = "--overwrite", defaultValue = "false",
        description = "Overwrite existing files when extracing zip")
    private boolean overwrite;

    @Option(names = "--artifact-pattern", required = true,
        description = "Regular expression that must match exactly one artifact")
    private Pattern artifactPattern;

    @Option(names = "--output-filename", description = "Prints artifact name", defaultValue = "false")
    private Boolean outputFilename;

    @Option(names = "--no-download", description = "Doesn't download artifact", defaultValue = "false")
    private Boolean noDownload;

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

        if (!Files.isDirectory(outputDirectory)) {
            try {
                Files.createDirectory(outputDirectory);
            } catch (IOException e) {
                log.error("Failed to create output directory {}", outputDirectory.toAbsolutePath().toString());
            }
        }

        try (SharedFetch sharedFetch = Fetch.sharedFetch("github download-artifact", sharedFetchArgs.options())) {
            final GithubClient client = new GithubClient(sharedFetch, parent.apiBaseUrl, parent.token);

            Mono<Artifact> candidate = resolveArtifact(client)
                .switchIfEmpty(Mono.error(new GenericException("Github client failed to find an artifact")))
                .doOnNext((artifact) -> System.out.println(artifact.getName()));

            if (noDownload) {
                candidate.block();
                return ExitCode.OK;
            }

            // Github API requires a token with "'Actions' repository
            // permissions (read)" to download an artifact
            // https://docs.github.com/en/rest/actions/artifacts?apiVersion=2026-03-10#download-an-artifact
            if (parent.token == null) {
                throw new IllegalArgumentException("Must provide a github token to query artifact data");
            }

            Path download = candidate
                .doOnNext(artifact -> {
                    log.info("Downloading artifact {}", artifact.getName());
                }) 
                .flatMap(artifact -> downloadArtifact(client, artifact))
                .block();


            if (unzip) {
                log.info("Unzipping artifact");
                unzipArtifact(download, outputDirectory, overwrite);
                Files.deleteIfExists(download);
            }
        }

        return ExitCode.OK;
    }

    /**
     * Resolves a GitHub workflow artifact.
     *
     * The user must provide exactly one selector:
     * a workflow name or ID, such as {@code build.yml}, or a workflow
     * run ID, such as {@code 12345678910}.
     *
     * A workflow selector resolves the latest successful run. A run ID resolves
     * that specific workflow run.
     *
     * @param client GithubClient to interact with Github api
     */
    private Mono<Artifact> resolveArtifact(GithubClient client) {
        if (runSelector.workflow == null && runSelector.runId == null) {
            return Mono.error(
                    new IllegalArgumentException("Workflow ID and Run ID not present, cannot resolve Github Artifact"));
        } else if (runSelector.workflow != null && runSelector.runId != null) {
            return Mono.error(new IllegalArgumentException("Workflow ID and Run ID present, please provide one value"));
        }

        if (runSelector.workflow != null) {
            return client.resolveArtifactForLatestSuccessfulWorkflow(organization, repo, runSelector.workflow,
                    artifactPattern);
        } else {
            return client.resolveArtifactForRun(organization, repo, runSelector.runId, artifactPattern);
        }
    }

    /**
     * Extracts zip file.
     *
     * @param zipPath        Path to zip file we are extracting
     * @param directoryPath  Path to directory we are extracting zip to
     * @param overwriteFiles When extracting zip, do we overwrite existing files, or
     *                       only copy files that don't exist
     */
    static void unzipArtifact(Path zipPath, Path directoryPath, Boolean overwriteFiles) {
        final Path extractionRoot = directoryPath.toAbsolutePath().normalize();

        try {
            Files.createDirectories(extractionRoot);

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    final Path output = extractionRoot.resolve(entry.getName()).normalize();

                    if (!output.startsWith(extractionRoot)) {
                        throw new IOException("Invalid zip entry: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());

                        if (overwriteFiles) {
                            Files.copy(zis, output, StandardCopyOption.REPLACE_EXISTING);
                        } else if (Files.notExists(output)) {
                            Files.copy(zis, output);
                        }
                    }

                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract artifact archive", e);
        }
    }

    private Mono<Path> downloadArtifact(GithubClient client, Artifact artifact) {
        return client.downloadArtifact(artifact, outputDirectory.resolve(artifact.getName()));
    }
}