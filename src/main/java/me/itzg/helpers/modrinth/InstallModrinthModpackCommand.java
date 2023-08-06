package me.itzg.helpers.modrinth;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.*;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "install-modrinth-modpack",
    description = "Supports installation of Modrinth modpacks along with the associated mod loader",
    mixinStandardHelpOptions = true
)
@Slf4j
public class InstallModrinthModpackCommand implements Callable<Integer> {
    @Option(names = "--project", required = true,
        description = "One of" +
            "%n- Project ID or slug" +
            "%n- Project page URL" +
            "%n- Project file URL"
    )
    String modpackProject;

    @Option(names = {"--version-id", "--version"},
        description = "Version ID, name, or number from the file's metadata" +
            "%nDefault chooses newest file based on game version, loader, and/or default version type"
    )
    String version;

    @Option(names = "--game-version", description = "Applicable Minecraft version" +
        "%nDefault: (any)")
    String gameVersion;

    @Option(names = "--loader", description = "Valid values: ${COMPLETION-CANDIDATES}" +
        "%nDefault: (any)")
    ModpackLoader loader;

    @Option(names = "--default-version-type", defaultValue = "release", paramLabel = "TYPE",
        description = "Valid values: ${COMPLETION-CANDIDATES}" +
            "%nDefault: ${DEFAULT-VALUE}"
    )
    VersionType defaultVersionType;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--force-synchronize", defaultValue = "${env:MODRINTH_FORCE_SYNCHRONIZE:-false}")
    boolean forceSynchronize;

    @Option(names = "--force-modloader-reinstall", defaultValue = "${env:MODRINTH_FORCE_MODLOADER_REINSTALL:-false}")
    boolean forceModloaderReinstall;

    @Option(names = "--api-base-url", defaultValue = "${env:MODRINTH_API_BASE_URL:-https://api.modrinth.com}",
        description = "Default: ${DEFAULT-VALUE}"
    )
    String baseUrl;

    @CommandLine.ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws IOException {
        final ModrinthApiClient apiClient = new ModrinthApiClient(
            baseUrl, "install-modrinth-modpack", sharedFetchArgs.options());

        final ModrinthModpackManifest prevManifest = Manifests.load(
            outputDirectory, ModrinthModpackManifest.ID,
             ModrinthModpackManifest.class);

        final ProjectRef projectRef =
            ProjectRef.fromPossibleUrl(modpackProject, version);

        buildModpackFetcher(apiClient, projectRef)
            .fetchModpack(prevManifest)
            .flatMap(archivePath ->
                new ModrinthPackInstaller(apiClient, this.sharedFetchArgs,
                    archivePath, this.outputDirectory, this.resultsFile,
                    this.forceModloaderReinstall)
                .processModpack())
            .flatMap(installation ->
                Mono.just(ModrinthModpackManifest.builder()
                    .files(Manifests.relativizeAll(this.outputDirectory, installation.files))
                    .projectSlug(projectRef.getIdOrSlug())
                    .versionId(projectRef.getVersionId())
                    .dependencies(installation.index.getDependencies())
                    .build()))
            .handle((newManifest, sink) -> {
                try {
                    Manifests.cleanup(this.outputDirectory, prevManifest, newManifest, log);
                    Manifests.save(outputDirectory, ModrinthModpackManifest.ID, newManifest);
                } catch (IOException e) {
                    sink.error(e);
                }
            })
            .block();

        return ExitCode.OK;
    }

    private ModrinthPackFetcher buildModpackFetcher(
            ModrinthApiClient apiClient, ProjectRef projectRef)
    {
        if(projectRef.hasProjectUri()) {
            return new ModrinthHttpPackFetcher(
                apiClient, outputDirectory, projectRef.getProjectUri());
        } else {
            return new ModrinthApiPackFetcher(
                apiClient, projectRef, this.outputDirectory, this.gameVersion,
                this.defaultVersionType, this.loader.asLoader());
        }
    }
}
