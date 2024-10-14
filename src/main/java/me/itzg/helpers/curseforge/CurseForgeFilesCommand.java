package me.itzg.helpers.curseforge;

import static me.itzg.helpers.curseforge.CurseForgeApiClient.*;
import static me.itzg.helpers.curseforge.ModFileRefResolver.idsFrom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.cache.ApiCaching;
import me.itzg.helpers.cache.ApiCachingDisabled;
import me.itzg.helpers.cache.ApiCachingImpl;
import me.itzg.helpers.cache.CacheArgs;
import me.itzg.helpers.curseforge.CurseForgeFilesManifest.FileEntry;
import me.itzg.helpers.curseforge.model.Category;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.curseforge.model.FileDependency;
import me.itzg.helpers.curseforge.model.FileRelationType;
import me.itzg.helpers.curseforge.model.ModLoaderType;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.SharedFetchArgs;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Command(name = "curseforge-files", description = "Download and manage individual mod/plugin files from CurseForge")
@Slf4j
public class CurseForgeFilesCommand implements Callable<Integer> {

    public static final List<String> ALLOWED_CATEGORIES = Arrays.asList(
        CATEGORY_MC_MODS,
        CATEGORY_BUKKIT_PLUGINS
    );

    private static final Map<String/*categorySlug*/, String /*subdir*/> categorySubdirs = new HashMap<>();

    static {
        categorySubdirs.put(CATEGORY_MC_MODS, "mods");
        categorySubdirs.put(CATEGORY_BUKKIT_PLUGINS, "plugins");
    }

    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

    @Option(names = {"--output-directory", "-o"}, defaultValue = ".", paramLabel = "DIR")
    Path outputDir;

    @Option(names = "--default-category", description = "When providing slugs, a category is required to qualify those")
    public void setSlugCategory(String defaultCategory) {
        if (!ALLOWED_CATEGORIES.contains(defaultCategory)) {
            throw new InvalidParameterException("Category can only be one of " + String.join(", ", ALLOWED_CATEGORIES));
        }
        this.defaultCategory = defaultCategory;
    }

    String defaultCategory;

    @Option(names = "--api-base-url", defaultValue = "${env:CF_API_BASE_URL:-https://api.curseforge.com}",
        description = "Allows for overriding the CurseForge Eternal API used"
            + "%nCan also be passed via CF_API_BASE_URL")
    String apiBaseUrl;

    @Option(names = "--api-key", defaultValue = "${env:" + CurseForgeInstaller.API_KEY_VAR + "}",
        description = "An API key allocated from the Eternal developer console at "
            + CurseForgeInstaller.ETERNAL_DEVELOPER_CONSOLE_URL +
            "%nCan also be passed via " + CurseForgeInstaller.API_KEY_VAR
    )
    String apiKey;

    @Option(names = "--game-version", defaultValue = "${env:VERSION}",
        description = "The Minecraft version"
            + "%nCan also be passed via VERSION"
    )
    String gameVersion;

    @Option(names = "--mod-loader",
        description = "One of ${COMPLETION-CANDIDATES}"
    )
    ModLoaderType modLoaderType;

    @Option(names = "--disable-api-caching", defaultValue = "${env:CF_DISABLE_API_CACHING:-false}")
    boolean disableApiCaching;

    @ArgGroup(exclusive = false)
    CacheArgs cacheArgs;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Parameters(split = ",", paramLabel = "REF",
        description = "Can be <project ID>|<slug>':'<file ID>,"
            + " <project ID>|<slug>'@'<filename matcher>,"
            + " <project ID>|<slug>,"
            + " project page URL, file page URL,"
            + " '@'<filename with ref per line>"
            + "%nIf not specified, any previous mod/plugin files are removed")
    List<String> modFileRefs;

    @Override
    public Integer call() throws Exception {
        final CurseForgeFilesManifest oldManifest = Manifests.load(outputDir, CurseForgeFilesManifest.ID,
            CurseForgeFilesManifest.class
        );

        final Map<ModFileIds, FileEntry> previousFiles = buildPreviousFilesFromManifest(oldManifest);

        final CurseForgeFilesManifest newManifest;

        if (modFileRefs != null && !modFileRefs.isEmpty()) {
            try (
                final ApiCaching apiCaching = disableApiCaching ? new ApiCachingDisabled()
                    : new ApiCachingImpl(outputDir, CACHING_NAMESPACE, cacheArgs);
                final CurseForgeApiClient apiClient = new CurseForgeApiClient(
                    apiBaseUrl, apiKey, sharedFetchArgs.options(),
                    CurseForgeApiClient.MINECRAFT_GAME_ID,
                    apiCaching
                )
            ) {
                newManifest = apiClient.loadCategoryInfo(Arrays.asList(CATEGORY_MC_MODS, CATEGORY_BUKKIT_PLUGINS))
                    .flatMap(categoryInfo ->
                        processModFileRefs(categoryInfo, previousFiles, apiClient)
                            .map(entries -> CurseForgeFilesManifest.builder()
                                .entries(entries)
                                .build()))
                    .block();
            }
        }
        else {
            // nothing to install or requesting full cleanup
            newManifest = null;
        }

        if (oldManifest != null) {
            Manifests.cleanup(outputDir,
                mapFilePathsFromEntries(oldManifest),
                mapFilePathsFromEntries(newManifest),
                s -> log.info("Removing old file {}", s)
            );
        }
        if (newManifest != null && newManifest.getEntries() != null
            && !newManifest.getEntries().isEmpty()
        ) {
            Manifests.save(outputDir, CurseForgeFilesManifest.ID, newManifest);
        }
        else {
            Manifests.remove(outputDir, CurseForgeFilesManifest.ID);
        }

        return ExitCode.OK;
    }

    @NotNull
    private Mono<List<FileEntry>> processModFileRefs(CategoryInfo categoryInfo,
        Map<ModFileIds, FileEntry> previousFiles, CurseForgeApiClient apiClient
    ) {
        if (modFileRefs == null || modFileRefs.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        final ModFileRefResolver modFileRefResolver = new ModFileRefResolver(apiClient, categoryInfo);

        return modFileRefResolver.resolveModFiles(modFileRefs, defaultCategory, gameVersion, modLoaderType)
            .flatMapMany(modFiles -> {
                    final Set<Integer> requestedModIds = modFiles.stream()
                        .map(CurseForgeFile::getModId)
                        .collect(Collectors.toSet());

                    return Flux.fromIterable(modFiles)
                        .flatMap(modFile -> {

                            final Mono<FileEntry> retrieval;
                            final ModFileIds modFileIds = idsFrom(modFile);
                            final FileEntry entry = previousFiles.get(modFileIds);
                            if (entry != null
                                && Files.exists(outputDir.resolve(entry.getFilePath()))
                            ) {
                                log.debug("Mod file {} already exists at {}", modFile.getFileName(), entry.getFilePath());
                                retrieval = Mono.just(entry);
                            }
                            else {
                                retrieval = retrieveModFile(apiClient, categoryInfo, modFile)
                                    .map(path -> new FileEntry(modFileIds, outputDir.relativize(path).toString()));
                            }

                            return reportMissingDependencies(apiClient, modFile, requestedModIds)
                                .then(retrieval);
                        });
                }
            )
            .collectList();
    }

    /**
     *
     * @return flux of missing, required dependencies
     */
    private static Flux<Tuple2<CurseForgeMod, FileDependency>> reportMissingDependencies(CurseForgeApiClient apiClient,
        CurseForgeFile modFile, Set<Integer> requestedModIds
    ) {
        return Flux.fromIterable(modFile.getDependencies())
            .mapNotNull(dependency -> {
                if (!requestedModIds.contains(dependency.getModId())) {
                    switch (dependency.getRelationType()) {
                        case RequiredDependency:
                        case OptionalDependency:
                            return dependency;
                        default:
                            return null;
                    }
                }
                else {
                    return null;
                }
            })
            .flatMap(dependency ->
                apiClient.getModInfo(dependency.getModId())
                    .map(curseForgeMod -> Tuples.of(curseForgeMod, dependency))
            )
            .doOnNext(missingDep -> {
                    if (missingDep.getT2().getRelationType() == FileRelationType.RequiredDependency) {
                        log.warn("The mod file '{}' depends on mod project '{}' at {}, but it is not listed",
                            modFile.getDisplayName(), missingDep.getT1().getName(), missingDep.getT1().getLinks().getWebsiteUrl()
                        );
                    }
                    else if (missingDep.getT2().getRelationType() == FileRelationType.OptionalDependency) {
                        log.debug("The mod file '{}' optionally depends on mod project '{}', but it is not listed",
                            modFile.getDisplayName(), missingDep.getT1().getName()
                        );
                    }
            })
            .filter(missingDep -> missingDep.getT2().getRelationType() == FileRelationType.RequiredDependency);
    }

    @NotNull
    private static Map<ModFileIds, FileEntry> buildPreviousFilesFromManifest(CurseForgeFilesManifest oldManifest) {
        return oldManifest != null ?
            oldManifest.getEntries().stream()
                .collect(Collectors.toMap(
                    FileEntry::getIds,
                    fileEntry -> fileEntry,
                    // merge by picking first
                    (t, t2) -> {
                        log.warn("Duplicate files detected in previous manifest: {} vs {}", t, t2);
                        return t;
                    }
                ))
            : Collections.emptyMap();
    }

    @NotNull
    private Mono<Path> retrieveModFile(CurseForgeApiClient apiClient, CategoryInfo categoryInfo,
        CurseForgeFile curseForgeFile
    ) {
        return apiClient.getModInfo(curseForgeFile.getModId())
            .flatMap(curseForgeMod -> {
                    if (curseForgeFile.getDownloadUrl() == null) {
                        log.error("The authors of the mod '{}' have disallowed automated downloads. " +
                                "Manually download the file '{}' from {} and supply separately.",
                            curseForgeMod.getName(), curseForgeFile.getDisplayName(), curseForgeMod.getLinks().getWebsiteUrl()
                        );

                        return Mono.error(new InvalidParameterException(
                            String.format("The authors of %s do not allow automated downloads",
                                curseForgeMod.getName()
                            ))
                        );
                    }

                    return setupSubdir(categoryInfo, curseForgeMod)
                        .flatMap(subdir ->
                            apiClient.download(curseForgeFile,
                                outputDir.resolve(subdir).resolve(curseForgeFile.getFileName()),
                                modFileDownloadStatusHandler(outputDir, log)
                            )
                        );
                }
            )
            .checkpoint(String.format("Retrieving %d:%d", curseForgeFile.getModId(), curseForgeFile.getId()));
    }

    @NotNull
    private Mono<String> setupSubdir(CategoryInfo categoryInfo, CurseForgeMod curseForgeMod) {
        return Mono.defer(() -> {
                final Category category = categoryInfo.getCategory(curseForgeMod.getClassId());

                final String subdir = categorySubdirs.get(category.getSlug());
                if (subdir == null) {
                    return Mono.error(new InvalidParameterException(
                        String.format("Category %s does not have a known subdir", category.getName())));
                }

                try {
                    //noinspection BlockingMethodInNonBlockingContext due to subscribeOn
                    Files.createDirectories(outputDir.resolve(subdir));
                } catch (IOException e) {
                    return Mono.error(new GenericException("Failed to create mod file directory", e));
                }

                return Mono.just(subdir);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    private static List<String> mapFilePathsFromEntries(CurseForgeFilesManifest oldManifest) {
        return
            oldManifest != null ?
            oldManifest.entries.stream().map(FileEntry::getFilePath).collect(Collectors.toList())
            : null;
    }

}
