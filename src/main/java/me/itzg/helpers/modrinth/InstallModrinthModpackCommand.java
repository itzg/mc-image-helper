package me.itzg.helpers.modrinth;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.*;
import me.itzg.helpers.quilt.QuiltInstaller;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static me.itzg.helpers.modrinth.ModrinthApiClient.pickVersionFile;

@CommandLine.Command(name = "install-modrinth-modpack",
    description = "Supports installation of Modrinth modpacks along with the associated mod loader",
    mixinStandardHelpOptions = true
)
@Slf4j
public class InstallModrinthModpackCommand implements Callable<Integer> {
    private final static Pattern MODPACK_PAGE_URL = Pattern.compile(
        "https://modrinth.com/modpack/(?<slug>.+?)(/version/(?<versionName>.+))?"
    );

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

        new ModrinthApiPackFetcher(
            apiClient, projectRef, this.outputDirectory, this.gameVersion,
            this.defaultVersionType, this.loader.asLoader())
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
}
