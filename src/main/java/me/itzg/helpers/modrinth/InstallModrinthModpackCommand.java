package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.PathOrUri;
import me.itzg.helpers.http.PathOrUriConverter;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.model.VersionType;
import org.jetbrains.annotations.VisibleForTesting;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@CommandLine.Command(name = "install-modrinth-modpack",
    description = "Supports installation of Modrinth modpacks along with the associated mod loader",
    mixinStandardHelpOptions = true
)
@Slf4j
public class InstallModrinthModpackCommand implements Callable<Integer> {
    @Option(names = "--project", required = true,
        description = "One of"
            + "%n- Project ID or slug"
            + "%n- Project page URL"
            + "%n- Project file URL"
            + "%n- Custom URL of a hosted modpack file"
            + "%n- Local path to a modpack file"
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

    @Option(names = "--ignore-missing-files",
        split = McImageHelper.SPLIT_COMMA_NL, splitSynopsisLabel = McImageHelper.SPLIT_SYNOPSIS_COMMA_NL,
        description = "These files will be ignored when evaluating if the modpack is up to date"
    )
    List<String> ignoreMissingFiles;

    @Option(names = "--exclude-files",
        split = McImageHelper.SPLIT_COMMA_NL, splitSynopsisLabel = McImageHelper.SPLIT_SYNOPSIS_COMMA_NL,
        description = "Files to exclude, such as improperly declared client mods. It will match any part of the file's name/path."
    )
    List<String> excludeFiles;

    @Option(names = "--force-include-files",
        split = McImageHelper.SPLIT_COMMA_NL, splitSynopsisLabel = McImageHelper.SPLIT_SYNOPSIS_COMMA_NL,
        description = "Files to force include that were marked as non-server mods. It will match any part of the file's name/path."
    )
    List<String> forceIncludeFiles;

    @Option(names = "--overrides-exclusions",
        split = "\n|,", splitSynopsisLabel = "NL or ,",
        description = "Excludes files from the overrides that match these ant-style patterns\n"
            + "*  : matches any non-slash characters\n"
            + "** : matches any characters\n"
            + "?  : matches one character"
    )
    List<String> overridesExclusions;

    @Option(names = "--default-exclude-includes", paramLabel = "FILE|URI",
        description = "A JSON file that contains global and per modpack exclude/include declarations. "
            + "See README for schema.",
        converter = PathOrUriConverter.class
    )
    PathOrUri defaultExcludeIncludes;

    @CommandLine.ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws IOException {

        final ModrinthModpackManifest prevManifest;
        final ModrinthModpackManifest newManifest;

        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-modrinth-modpack", sharedFetchArgs.options())) {
            final ModrinthApiClient apiClient = new ModrinthApiClient(
                baseUrl, sharedFetch
            );

            prevManifest = Manifests.load(
                outputDirectory, ModrinthModpackManifest.ID,
                ModrinthModpackManifest.class
            );

            final ProjectRef projectRef = ProjectRef.fromPossibleUrl(modpackProject, version);

            newManifest = buildModpackFetcher(apiClient, projectRef)
                .fetchModpack(prevManifest)
                .onErrorMap(throwable -> throwable instanceof NoApplicableVersionsException || throwable instanceof NoFilesAvailableException,
                    throwable -> new InvalidParameterException(throwable.getMessage(), throwable)
                )
                .flatMap(fetchedPack ->
                    installerFactory.create(
                            apiClient,
                            fetchedPack.getMrPackFile(),
                            createFileInclusionCalculator(
                                fetchedPack.getProjectSlug(),
                                sharedFetch
                            )
                        )
                        .setOverridesExclusions(overridesExclusions)
                        .processModpack(sharedFetch)
                        .flatMap(installation -> {
                            if (resultsFile != null) {
                                return processResultsFile(fetchedPack, installation);
                            }
                            else {
                                return Mono.just(installation);
                            }
                        })
                        .map(installation ->
                            ModrinthModpackManifest.builder()
                                .files(Manifests.relativizeAll(this.outputDirectory, installation.files))
                                .projectSlug(fetchedPack.getProjectSlug())
                                .versionId(fetchedPack.getVersionId())
                                .dependencies(installation.index.getDependencies())
                                .build())
                )
                .block();
        }

        if (newManifest != null) {
            Manifests.cleanup(this.outputDirectory, prevManifest, newManifest, log);
            Manifests.save(outputDirectory, ModrinthModpackManifest.ID, newManifest);
        }

        return ExitCode.OK;
    }

    private FileInclusionCalculator createFileInclusionCalculator(
        String projectSlug,
        SharedFetch sharedFetch
    ) {

        final ExcludeIncludesContent excludeIncludesContent;
        if (defaultExcludeIncludes == null) {
            excludeIncludesContent = null;
        }
        else if (defaultExcludeIncludes.getPath() != null) {
            try {
                excludeIncludesContent = ObjectMappers.defaultMapper()
                    .readValue(defaultExcludeIncludes.getPath().toFile(), ExcludeIncludesContent.class);
            } catch (IOException e) {
                throw new GenericException("Failed to read exclude/include file", e);
            }
        }
        else {
            excludeIncludesContent = sharedFetch.fetch(defaultExcludeIncludes.getUri())
                .toObject(ExcludeIncludesContent.class)
                .acceptContentTypes(null)
                .execute();
        }

        return new FileInclusionCalculator(projectSlug, excludeFiles, forceIncludeFiles, excludeIncludesContent);
    }

    @VisibleForTesting
    @FunctionalInterface
    interface ModrinthModpackInstallerFactory {

        ModrinthPackInstaller create(ModrinthApiClient apiClient, Path mrPackFile,
            FileInclusionCalculator fileInclusionCalculator
            );
    }

    @VisibleForTesting
    @Setter(AccessLevel.PACKAGE)
    private ModrinthModpackInstallerFactory installerFactory = (apiClient, mrPackFile, fileInclusionCalculator) ->
        new ModrinthPackInstaller(
            apiClient, this.sharedFetchArgs.options(),
            mrPackFile, this.outputDirectory, this.resultsFile,
            this.forceModloaderReinstall,
            fileInclusionCalculator
        );

    private Mono<Installation> processResultsFile(FetchedPack fetchedPack, Installation installation) {
        return Mono.fromCallable(() -> {
                try (ResultsFileWriter results = new ResultsFileWriter(resultsFile, true)) {
                    results.write(ResultsFileWriter.MODPACK_NAME, installation.getIndex().getName());
                    results.write(ResultsFileWriter.MODPACK_VERSION, fetchedPack.getVersionNumber());
                }
                return installation;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    private ModrinthPackFetcher buildModpackFetcher(
            ModrinthApiClient apiClient, ProjectRef projectRef)
    {
        if (projectRef.isFileUri()) {
            return new FilePackFetcher(projectRef);
        }
        else if (projectRef.hasProjectUri()) {
            return new ModrinthHttpPackFetcher(
                apiClient, outputDirectory, projectRef.getProjectUri());
        } else {
            return new ModrinthApiPackFetcher(
                apiClient,
                projectRef,
                forceSynchronize,
                outputDirectory,
                gameVersion,
                defaultVersionType, loader != null ? loader.asLoader() : null
            )
                .setIgnoreMissingFiles(ignoreMissingFiles);
        }
    }
}
