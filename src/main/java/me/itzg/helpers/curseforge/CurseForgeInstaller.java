package me.itzg.helpers.curseforge;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static me.itzg.helpers.curseforge.CurseForgeApiClient.CACHING_NAMESPACE;
import static me.itzg.helpers.curseforge.CurseForgeApiClient.modFileDownloadStatusHandler;
import static me.itzg.helpers.singles.MoreCollections.safeStreamFrom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.cache.ApiCaching;
import me.itzg.helpers.cache.ApiCachingDisabled;
import me.itzg.helpers.cache.ApiCachingImpl;
import me.itzg.helpers.cache.CacheArgs;
import me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes;
import me.itzg.helpers.curseforge.OverridesApplier.Result;
import me.itzg.helpers.curseforge.model.Category;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.curseforge.model.ManifestFileRef;
import me.itzg.helpers.curseforge.model.ManifestType;
import me.itzg.helpers.curseforge.model.MinecraftModpackManifest;
import me.itzg.helpers.curseforge.model.ModLoader;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.errors.RateLimitException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.forge.ForgeInstallerResolver;
import me.itzg.helpers.forge.NeoForgeInstallerResolver;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@RequiredArgsConstructor
@Slf4j
public class CurseForgeInstaller {

    public static final String API_KEY_VAR = "CF_API_KEY";
    public static final String MODPACK_ZIP_VAR = "CF_MODPACK_ZIP";

    public static final String ETERNAL_DEVELOPER_CONSOLE_URL = "https://console.curseforge.com/";
    public static final String CURSEFORGE_ID = "curseforge";
    public static final String REPO_SUBDIR_MODPACKS = "modpacks";
    public static final String REPO_SUBDIR_MODS = "mods";
    public static final String REPO_SUBDIR_WORLDS = "worlds";
    private static final Duration BAD_FILE_DELAY = Duration.ofSeconds(5);
    public static final int BAD_FILE_ATTEMPTS = 3;

    private final Path outputDir;
    private final Path resultsFile;

    @Getter
    @Setter
    private String apiBaseUrl = "https://api.curseforge.com";

    @Getter
    @Setter
    private String apiKey;

    @Getter
    @Setter
    private boolean forceSynchronize;

    @Getter @Setter
    private ExcludeIncludesContent excludeIncludes;

    /**
     * @see InstallCurseForgeCommand#levelFrom
     */
    @Getter @Setter
    private LevelFrom levelFrom;

    @Getter @Setter
    private boolean overridesSkipExisting;

    @Getter @Setter
    private SharedFetch.Options sharedFetchOptions;

    @Getter @Setter
    private Path downloadsRepo;

    private final Set<String> applicableClassIdSlugs = new HashSet<>(Arrays.asList(
        CurseForgeApiClient.CATEGORY_MODPACKS,
        CurseForgeApiClient.CATEGORY_MC_MODS,
        CurseForgeApiClient.CATEGORY_BUKKIT_PLUGINS,
        CurseForgeApiClient.CATEGORY_WORLDS
    ));

    @Getter @Setter
    private List<String> overridesExclusions;

    @Getter @Setter
    private boolean forceReinstallModloader;

    @Getter @Setter
    private List<String> ignoreMissingFiles;

    @Getter @Setter
    private boolean disableApiCaching;

    @Getter @Setter
    private CacheArgs cacheArgs;

    /**
     */
    public void installFromModpackZip(Path modpackZip, String slug) {
        requireNonNull(modpackZip, "modpackZip is required");

        install(slug, context -> {
            final MinecraftModpackManifest modpackManifest = extractModpackManifest(modpackZip);

            processModpackManifest(context, modpackManifest,
                new OverridesFromZipApplier(
                    outputDir, modpackZip, overridesSkipExisting,
                    modpackManifest.getOverrides(),
                    levelFrom, overridesExclusions
                )
            );
        });
    }

    /**
     */
    public void installFromModpackManifest(String modpackManifestLoc, String slug) {
        requireNonNull(modpackManifestLoc, "modpackManifest is required");

        install(slug, context -> {
            final MinecraftModpackManifest modpackManifest;
            if (Uris.isUri(modpackManifestLoc)) {
                modpackManifest = Fetch.fetch(URI.create(modpackManifestLoc))
                    .toObject(MinecraftModpackManifest.class)
                    .assemble()
                    .block();
                if (modpackManifest == null) {
                    throw new GenericException("Modpack manifest could not be fetched");
                }
            }
            else {
                modpackManifest = ObjectMappers.defaultMapper()
                    .readValue(new File(modpackManifestLoc), MinecraftModpackManifest.class);
            }

            processModpackManifest(context, modpackManifest,
                () -> new Result(Collections.emptyList(), null)
            );
        });
    }

    /**
     * @throws MissingModsException if any mods need to be manually downloaded
     */
    public void install(String slug, String fileMatcher, Integer fileId) throws IOException {

        install(slug, context ->
            installByRetrievingModpackZip(context, fileMatcher, fileId)
        );
    }

    void install(String slug, InstallationEntryPoint entryPoint) {
        requireNonNull(outputDir, "outputDir is required");
        requireNonNull(slug);
        requireNonNull(entryPoint);

        final CurseForgeManifest manifest = Manifests.load(outputDir, CURSEFORGE_ID, CurseForgeManifest.class);
        // to adapt to previous copies of manifest
        trimLevelsContent(manifest);

        if (apiKey == null || apiKey.isEmpty()) {
            if (manifest != null) {
                log.warn("API key is not set, so will re-use previous modpack installation of {}",
                    manifest.getSlug() != null ? manifest.getSlug() : "Project ID " + manifest.getModId()
                );
                log.warn("Obtain an API key from " + ETERNAL_DEVELOPER_CONSOLE_URL
                    + " and set the environment variable " + API_KEY_VAR + " in order to restore full functionality.");
                return;
            }
            else {
                throw new InvalidParameterException("API key is not set. Obtain an API key from " + ETERNAL_DEVELOPER_CONSOLE_URL
                    + " and set the environment variable " + API_KEY_VAR);
            }
        }

        try (
            final ApiCaching apiCaching = disableApiCaching ? new ApiCachingDisabled()
                : new ApiCachingImpl(outputDir, CACHING_NAMESPACE, cacheArgs);
            final CurseForgeApiClient cfApi = new CurseForgeApiClient(
                apiBaseUrl, apiKey, sharedFetchOptions,
                CurseForgeApiClient.MINECRAFT_GAME_ID,
                apiCaching
            )
        ) {
            final CategoryInfo categoryInfo = cfApi.loadCategoryInfo(applicableClassIdSlugs)
                .block();

            entryPoint.install(
                new InstallContext(slug, cfApi, categoryInfo, manifest)
            );

        } catch (FailedRequestException e) {
            if (e.getStatusCode() == 403) {
                log.debug("Failed request details: {}", e.toString());

                if (e.getBody().contains("There might be too much traffic")) {
                    throw new RateLimitException(null, String.format("Access to %s has been rate-limited.", apiBaseUrl), e);
                }
                else {
                    throw new InvalidParameterException(String.format("Access to %s is forbidden or rate-limit has been exceeded."
                            + " Ensure %s is set to a valid API key from %s or allow rate-limit to reset.",
                        apiBaseUrl, API_KEY_VAR, ETERNAL_DEVELOPER_CONSOLE_URL
                    ), e);
                }
            }
            else {
                throw e;
            }
        } catch (IOException e) {
            throw new GenericException("Failed to setup API caching", e);
        }
    }

    @AllArgsConstructor
    static class InstallContext {

        private final String slug;
        private final CurseForgeApiClient cfApi;
        private final CategoryInfo categoryInfo;
        private final CurseForgeManifest prevInstallManifest;
    }

    interface InstallationEntryPoint {

        void install(InstallContext context) throws IOException;
    }

    private void installByRetrievingModpackZip(InstallContext context, String fileMatcher, Integer fileId) throws IOException {
        final CurseForgeMod curseForgeMod = context.cfApi.searchMod(context.slug,
                context.categoryInfo.getClassIdForSlug(CurseForgeApiClient.CATEGORY_MODPACKS)
            )
            .block();

        resolveModpackFileAndProcess(context, curseForgeMod, fileId, fileMatcher);
    }

    /**
     * @throws MissingModsException if any mods need to be manually downloaded
     */
    private void processModpackManifest(InstallContext context,
        MinecraftModpackManifest modpackManifest, OverridesApplier overridesApplier
    ) throws IOException {

        final String modpackName = modpackManifest.getName();
        final String modpackVersion = modpackManifest.getVersion();
        // absolute value, so negative IDs don't look weird
        final int pseudoModId = Math.abs(modpackName.hashCode());
        final int pseudoFileId = Math.abs(hashModpackFileReferences(modpackManifest.getFiles()));

        if (matchesPreviousInstall(context, pseudoModId, pseudoFileId)) {
            if (forceSynchronize) {
                log.info("Requested force synchronize of {}", modpackName);
            }
            else if (Manifests.allFilesPresent(outputDir, context.prevInstallManifest, ignoreMissingFiles)) {
                log.info("Requested CurseForge modpack {} is already installed", modpackName);

                finalizeExistingInstallation(context.prevInstallManifest);

                return;
            }
            else {
                log.warn("Re-installing due to missing files from modpack: {}",
                    Manifests.missingFiles(outputDir, context.prevInstallManifest)
                );
            }
        }

        log.info("Installing modpack '{}' version {} from provided modpack zip",
            modpackName, modpackVersion
        );

        final ModPackResults results = processModpack(context, modpackManifest, overridesApplier);

        finalizeResults(context, results,
            pseudoModId, pseudoFileId, results.getName()
        );
    }

    private static boolean matchesPreviousInstall(InstallContext context, int modId, int fileId) {
        return context.prevInstallManifest != null
            && (context.prevInstallManifest.getModId() == modId
            || Objects.equals(context.prevInstallManifest.getSlug(), context.slug))
            && context.prevInstallManifest.getFileId() == fileId;
    }

    private int hashModpackFileReferences(List<ManifestFileRef> files) {
        // seed the hash with a prime
        int hash = 7;
        for (ManifestFileRef file : files) {
            hash = 31 * hash + file.getProjectID();
            hash = 31 * hash + file.getFileID();
        }
        return hash;
    }

    /**
     * @throws MissingModsException if any mods need to be manually downloaded
     */
    private void resolveModpackFileAndProcess(
        InstallContext context,
        CurseForgeMod mod, Integer fileId,
        String fileMatcher
    )
        throws IOException {

        final CurseForgeFile modFile;

        if (fileId == null) {
            modFile = context.cfApi.resolveModpackFile(mod, fileMatcher);
        }
        else {
            modFile = context.cfApi.getModFileInfo(mod.getId(), fileId)
                .switchIfEmpty(Mono.error(() -> new GenericException("Unable to resolve modpack's file")))
                .block();
        }

        //noinspection DataFlowIssue handled by switchIfEmpty
        if (matchesPreviousInstall(context, modFile.getModId(), modFile.getId())) {
            if (forceSynchronize) {
                log.info("Requested force synchronize of {}", modFile.getDisplayName());
            }
            else if (Manifests.allFilesPresent(outputDir, context.prevInstallManifest, ignoreMissingFiles)) {
                log.info("Requested CurseForge modpack {} is already installed for {}",
                    modFile.getDisplayName(), mod.getName()
                );

                finalizeExistingInstallation(context.prevInstallManifest);

                return;
            }
            else {
                log.warn("Re-installing due to missing files from modpack: {}",
                    Manifests.missingFiles(outputDir, context.prevInstallManifest)
                );
            }
        }

        final Path modpackZip;
        if (modFile.getDownloadUrl() == null) {
            modpackZip = locateModpackInRepo(modFile.getFileName());
            if (modpackZip == null) {
                throw new GenericException(String.format(
                    "The modpack authors have indicated this file is not allowed for project distribution." +
                        " Please download the client zip file from %s and pass via %s environment variable" +
                        " or place in downloads repo directory.",
                    ofNullable(mod.getLinks().getWebsiteUrl()).orElse(" their CurseForge page"),
                    MODPACK_ZIP_VAR
                ));
            }
        }
        else {
            modpackZip = context.cfApi.downloadTemp(modFile, ".zip",
                    (status, uri, file) ->
                        log.debug("Modpack file retrieval: status={} uri={} file={}", status, uri, file)
                )
                .block();
        }

        log.info("Processing modpack '{}' ({}) @ {}:{}", modFile.getDisplayName(),
            mod.getSlug(), modFile.getModId(), modFile.getId()
        );

        if (modpackZip == null) {
            throw new GenericException("Download of modpack zip was empty");
        }

        /*downloadModpackZip(context, modFile);*/
        final ModPackResults results;
        try {

            final MinecraftModpackManifest modpackManifest = extractModpackManifest(modpackZip);
            results = processModpack(context, modpackManifest,
                new OverridesFromZipApplier(
                    outputDir, modpackZip, overridesSkipExisting,
                    modpackManifest.getOverrides(),
                    levelFrom, overridesExclusions
                )
            );
        } finally {
            Files.delete(modpackZip);
        }

        finalizeResults(context, results, modFile.getModId(), modFile.getId(), modFile.getDisplayName());
    }

    private Path locateModpackInRepo(String fileName) {
        if (downloadsRepo == null) {
            return null;
        }

        return locateFileIn(fileName, downloadsRepo, downloadsRepo.resolve(REPO_SUBDIR_MODPACKS));
    }

    private Path locateModInRepo(String fileName) {
        if (downloadsRepo == null) {
            return null;
        }

        return locateFileIn(fileName, downloadsRepo, downloadsRepo.resolve(REPO_SUBDIR_MODS));
    }

    private Path locateWorldZipInRepo(String fileName) {
        if (downloadsRepo == null) {
            return null;
        }

        return locateFileIn(fileName, downloadsRepo, downloadsRepo.resolve(REPO_SUBDIR_WORLDS));
    }

    private static Path locateFileIn(String fileName, @Nullable Path... dirs) {
        for (Path dir : dirs) {
            if (dir != null) {
                final Path resolved = dir.resolve(fileName);
                if (Files.exists(resolved)) {
                    return resolved;
                }

                // When downloading, the browser may replace spaces with +'s
                final Path altResolved = dir.resolve(fileName.replace(' ', '+'));
                if (Files.exists(altResolved)) {
                    return altResolved;
                }
            }
        }
        return null;
    }

    private void finalizeExistingInstallation(CurseForgeManifest prevManifest) throws IOException {
        // Double-check the mod loader is still present and ready
        if (prevManifest.getMinecraftVersion() != null && prevManifest.getModLoaderId() != null) {
            prepareModLoader(prevManifest.getModLoaderId(), prevManifest.getMinecraftVersion());
        }

        // ...and write out level name from previous run
        if (resultsFile != null) {
            try (ResultsFileWriter resultsFileWriter = new ResultsFileWriter(resultsFile, true)) {
                if (prevManifest.getLevelName() != null) {
                    resultsFileWriter.write("LEVEL", prevManifest.getLevelName());
                }
                resultsFileWriter.write(ResultsFileWriter.MODPACK_NAME, prevManifest.getModpackName());
                resultsFileWriter.write(ResultsFileWriter.MODPACK_VERSION, prevManifest.getModpackVersion());
            }
        }
    }

    /**
     * @throws MissingModsException if any mods need to be manually downloaded
     */
    private void finalizeResults(InstallContext context, ModPackResults results, int modId, int fileId, String displayName)
        throws IOException {
        final CurseForgeManifest newManifest = CurseForgeManifest.builder()
            .modpackName(results.getName())
            .modpackVersion(results.getVersion())
            .slug(context.slug)
            .modId(modId)
            .fileId(fileId)
            .fileName(displayName)
            .files(Manifests.relativizeAll(outputDir, results.getFiles()))
            .minecraftVersion(results.getMinecraftVersion())
            .modLoaderId(results.getModLoaderId())
            .levelName(results.getLevelName())
            .build();

        Manifests.cleanup(outputDir, context.prevInstallManifest, newManifest, log);

        Manifests.save(outputDir, CURSEFORGE_ID, newManifest);

        if (!results.getNeedsDownload().isEmpty()) {
            throw new MissingModsException(results.getNeedsDownload());
        }

        if (resultsFile != null) {
            try (ResultsFileWriter resultsFileWriter = new ResultsFileWriter(resultsFile, true)) {
                if (results.getLevelName() != null) {
                    resultsFileWriter.write("LEVEL", results.getLevelName());
                }
                resultsFileWriter.write(ResultsFileWriter.MODPACK_NAME, results.getName());
                resultsFileWriter.write(ResultsFileWriter.MODPACK_VERSION, results.getVersion());
            }
        }
    }

    private void trimLevelsContent(CurseForgeManifest manifest) {
        if (manifest == null) {
            return;
        }

        final Optional<String> levelDirPrefix = manifest.getFiles().stream()
            .map(Paths::get)
            .filter(p -> p.getFileName().toString().equals("level.dat"))
            .findFirst()
            .map(p -> p.getParent().toString());

        levelDirPrefix.ifPresent(prefix -> {
            log.debug("Found old manifest files with a world prefix={}", prefix);
            manifest.setFiles(
                manifest.getFiles().stream()
                    .filter(oldEntry -> !oldEntry.startsWith(prefix))
                    .collect(Collectors.toList())
            );
        });
    }

    private ModPackResults processModpack(InstallContext context,
        MinecraftModpackManifest modpackManifest, OverridesApplier overridesApplier
    ) throws IOException {
        if (modpackManifest.getManifestType() != ManifestType.minecraftModpack) {
            throw new InvalidParameterException("The zip file provided does not seem to be a Minecraft modpack");
        }

        final ModLoader modLoader = modpackManifest.getMinecraft().getModLoaders().stream()
            .filter(ModLoader::isPrimary)
            .findFirst()
            .orElseThrow(() -> new GenericException("Unable to find primary mod loader in modpack"));

        final OutputPaths outputPaths = new OutputPaths(
            Files.createDirectories(outputDir.resolve("mods")),
            Files.createDirectories(outputDir.resolve("plugins")),
            Files.createDirectories(outputDir.resolve("saves"))
        );

        final ExcludeIncludeIds excludeIncludeIds = resolveExcludeIncludes(context);
        log.debug("Using {}", excludeIncludeIds);

        // Go through all the files listed in modpack (given project ID + file ID)
        final List<PathWithInfo> modFiles = Flux.fromIterable(modpackManifest.getFiles())
            // ...does the modpack even say it's required?
            .filter(ManifestFileRef::isRequired)
            // ...is this mod file excluded because it is a client mod that didn't declare as such
            .filterWhen(manifestFileRef -> {
                final int projectID = manifestFileRef.getProjectID();
                final boolean exclude = excludeIncludeIds.getExcludeIds().contains(projectID);
                final boolean forceInclude = excludeIncludeIds.getForceIncludeIds().contains(projectID);

                log.debug("Evaluating projectId={} with exclude={} and forceInclude={}",
                    projectID, exclude, forceInclude
                );

                return Mono.just(forceInclude || !exclude)
                    .flatMap(proceed -> proceed ? Mono.just(true)
                        : context.cfApi.getModInfo(projectID)
                            .map(mod -> {
                                log.info("Excluding mod file '{}' ({}) due to configuration",
                                    mod.getName(), mod.getSlug()
                                );
                                // and filter away
                                return false;
                            })
                    );
            })
            // ...download and possibly unzip world file
            .flatMap(fileRef ->
                processFileFromModpack(context, outputPaths,
                    fileRef.getProjectID(), fileRef.getFileID(),
                    excludeIncludeIds.getForceIncludeIds(),
                    context.categoryInfo
                )
                    .checkpoint()
            )
            .collectList()
            .block();

        final Result overridesResult = overridesApplier.apply();

        prepareModLoader(modLoader.getId(), modpackManifest.getMinecraft().getVersion());

        return buildResults(modpackManifest, modLoader, modFiles, overridesResult);
    }

    private ModPackResults buildResults(MinecraftModpackManifest modpackManifest, ModLoader modLoader,
        List<PathWithInfo> modFiles, Result overridesResult
    ) {
        return new ModPackResults()
            .setName(modpackManifest.getName())
            .setVersion(modpackManifest.getVersion())
            .setFiles(Stream.concat(
                        modFiles != null ?
                            // NOTE: this purposely includes files needing download to ensure
                            // they are considered for re-processing since they'll be missing still
                            modFiles.stream().map(PathWithInfo::getPath)
                            : Stream.empty(),
                        overridesResult.paths.stream()
                    )
                    .collect(Collectors.toList())
            )
            .setNeedsDownload(modFiles != null ?
                modFiles.stream()
                    .filter(PathWithInfo::isDownloadNeeded)
                    .collect(Collectors.toList())
                : Collections.emptyList()
            )
            .setLevelName(resolveLevelName(modFiles, overridesResult))
            .setMinecraftVersion(modpackManifest.getMinecraft().getVersion())
            .setModLoaderId(modLoader.getId());
    }

    private String resolveLevelName(List<PathWithInfo> modFiles, Result overridesResult) {
        if (levelFrom == LevelFrom.OVERRIDES && overridesResult.levelName != null) {
            return overridesResult.levelName;
        }
        else if (levelFrom == LevelFrom.WORLD_FILE && modFiles != null) {
            return modFiles.stream()
                .filter(pathWithInfo -> pathWithInfo.getLevelName() != null)
                .findFirst()
                .map(PathWithInfo::getLevelName)
                .orElse(null);
        }
        else {
            return null;
        }
    }

    private ExcludeIncludeIds resolveExcludeIncludes(InstallContext context) {
        if (excludeIncludes == null) {
            return new ExcludeIncludeIds(emptySet(), emptySet());
        }

        log.debug("Reconciling exclude/includes from given {}", excludeIncludes);

        final ExcludeIncludes specific =
            excludeIncludes.getModpacks() != null ? excludeIncludes.getModpacks().get(context.slug) : null;

        return Mono.zip(
                resolveFromSlugOrIds(
                    context, context.categoryInfo,
                    excludeIncludes.getGlobalExcludes(),
                    specific != null ? specific.getExcludes() : null
                ),
                resolveFromSlugOrIds(
                    context, context.categoryInfo,
                    excludeIncludes.getGlobalForceIncludes(),
                    specific != null ? specific.getForceIncludes() : null
                )
            )
            .map(tuple ->
                new ExcludeIncludeIds(tuple.getT1(), tuple.getT2())
            )
            .block();
    }

    private Mono<Set<Integer>> resolveFromSlugOrIds(
        InstallContext context, CategoryInfo categoryInfo,
        Collection<String> global, Collection<String> specific
    ) {
        log.trace("Resolving slug|id into IDs global={} specific={}", global, specific);

        return Flux.fromStream(
                Stream.concat(
                    safeStreamFrom(global), safeStreamFrom(specific)
                )
            )
            .flatMap(s -> {
                try {
                    final int id = Integer.parseInt(s);
                    return Mono.just(id);
                } catch (NumberFormatException e) {
                    return context.cfApi.slugToId(categoryInfo, s);
                }
            })
            .collect(Collectors.toSet());
    }

    /**
     * Downloads the referenced project-file into the appropriate subdirectory from outputPaths
     */
    private Mono<PathWithInfo> processFileFromModpack(
        InstallContext context, OutputPaths outputPaths,
        int projectID, int fileID,
        Set<Integer> forceIncludeIds,
        CategoryInfo categoryInfo
    ) {
        return context.cfApi.getModInfo(projectID)
            .flatMap(modInfo -> {
                final Category category = categoryInfo.contentClassIds.get(modInfo.getClassId());
                // applicable category?
                if (category == null) {
                    log.debug("Skipping project={} slug={} file={} since it is not an applicable classId={}",
                        projectID, modInfo.getSlug(), fileID, modInfo.getClassId()
                    );
                    return Mono.empty();
                }

                final Path outputDir;
                final boolean isWorld;
                if (category.getSlug().endsWith("-mods")) {
                    outputDir = outputPaths.getModsDir();
                    isWorld = false;
                }
                else if (category.getSlug().endsWith("-plugins")) {
                    outputDir = outputPaths.getPluginsDir();
                    isWorld = false;
                }
                else if (category.getSlug().equals("worlds")) {
                    outputDir = outputPaths.getWorldsDir();
                    isWorld = true;
                }
                else {
                    return Mono.error(
                        new GenericException(
                            String.format("Unsupported category type=%s from mod=%s", category.getSlug(), modInfo.getSlug()))
                    );
                }

                return context.cfApi.getModFileInfo(projectID, fileID)
                    .flatMap(cfFile -> {
                        if (!forceIncludeIds.contains(projectID) && !isServerMod(cfFile)) {
                            log.debug("Skipping {} since it is a client mod", cfFile.getFileName());
                            return Mono.empty();
                        }
                        log.debug("Download/confirm mod {} @ {}:{}",
                            // several mods have non-descriptive display names, like "v1.0.0", so filename tends to be better
                            cfFile.getFileName(),
                            projectID, fileID
                        );

                        final Mono<ResolveResult> resolvedFileMono =
                            Mono.defer(() ->
                                    downloadOrResolveFile(context, modInfo, isWorld, outputDir, cfFile)
                                        .checkpoint()
                                )
                                // retry the deferred part above if one of the expected failure cases
                                .retryWhen(
                                    Retry.fixedDelay(BAD_FILE_ATTEMPTS, BAD_FILE_DELAY)
                                        .filter(throwable ->
                                            throwable instanceof FileHashInvalidException ||
                                            throwable instanceof FailedRequestException
                                        )
                                        .doBeforeRetry(retrySignal ->
                                            log.warn("Retrying to download {} @ {}:{}",
                                            cfFile.getFileName(), projectID, fileID)
                                        )
                                );

                        return isWorld ?
                            resolvedFileMono
                                .map(resolveResult ->
                                    resolveResult.downloadNeeded ?
                                        new PathWithInfo(resolveResult.path)
                                            .setDownloadNeeded(resolveResult.downloadNeeded)
                                            .setModInfo(modInfo)
                                            .setCurseForgeFile(cfFile)
                                        : extractWorldZip(modInfo, resolveResult.path, outputPaths.getWorldsDir())
                                )
                            : resolvedFileMono
                                .map(resolveResult ->
                                    new PathWithInfo(resolveResult.path)
                                        .setDownloadNeeded(resolveResult.downloadNeeded)
                                        .setModInfo(modInfo)
                                        .setCurseForgeFile(cfFile)
                                );
                    });
            })
            .checkpoint(String.format("Downloading file from modpack %d:%d", projectID, fileID));
    }

    @RequiredArgsConstructor
    static class ResolveResult {

        final Path path;
        @Setter
        boolean downloadNeeded;
    }

    /**
     * @param outputDir the mods, plugins, etc directory to place the mod file
     */
    private Mono<ResolveResult> downloadOrResolveFile(InstallContext context, CurseForgeMod modInfo,
        boolean isWorld, Path outputDir, CurseForgeFile cfFile
    ) {
        final Path outputFile = outputDir.resolve(cfFile.getFileName());

        // Will try to locate an existing file by alternate names that browser might create,
        // but only for non-world files of the modpack
        final Path locatedFile = !isWorld ?
            locateFileIn(cfFile.getFileName(), outputDir)
            : null;

        if (locatedFile != null) {
            log.info("Mod file {} already exists", locatedFile);
            return verifyHash(cfFile, locatedFile)
                .map(ResolveResult::new);
        }
        else {
            final Path fileInRepo = locateModInRepo(cfFile.getFileName());
            if (fileInRepo != null) {
                return copyFromDownloadsRepo(outputFile, fileInRepo);
            }

            return context.cfApi.download(cfFile, outputFile, modFileDownloadStatusHandler(this.outputDir, log))
                .flatMap(path -> verifyHash(cfFile, path))
                .map(ResolveResult::new)
                .onErrorResume(
                    e -> e instanceof FailedRequestException
                        && ((FailedRequestException) e).getStatusCode() == 404,
                    failedRequestException -> handleFileNeedingManualDownload(modInfo, isWorld, cfFile, outputFile)
                );
        }
    }

    private static Mono<Path> verifyHash(CurseForgeFile cfFile, Path path) {
        return FileHashVerifier.verify(path, cfFile.getHashes())
            .onErrorResume(IllegalArgumentException.class::isInstance,
                e -> {
                    log.warn("Unable to process hash for mod {}: {}", cfFile, e.getMessage());
                    return Mono.just(path);
                }
            );
    }

    private Mono<ResolveResult> handleFileNeedingManualDownload(CurseForgeMod modInfo, boolean isWorld, CurseForgeFile cfFile,
        Path outputFile
    ) {
        final Path resolved =
            isWorld ?
                locateWorldZipInRepo(cfFile.getFileName())
                : locateModInRepo(cfFile.getFileName());
        if (resolved == null) {
            log.warn("The authors of the mod '{}' have disallowed project distribution. " +
                    "Manually download the file '{}' from {} and supply via downloads repo or separately.",
                modInfo.getName(), cfFile.getDisplayName(), modInfo.getLinks().getWebsiteUrl()
            );
            return Mono.just(new ResolveResult(outputFile).setDownloadNeeded(true));
        }
        else {
            return copyFromDownloadsRepo(outputFile, resolved);
        }
    }

    private @NotNull Mono<ResolveResult> copyFromDownloadsRepo(Path outputFile, Path resolved) {
        return
            Mono.fromCallable(() -> {
                    log.info("Mod file {} obtained from downloads repo",
                        this.outputDir.relativize(outputFile)
                    );
                    return Files.copy(resolved, outputFile);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResolveResult::new);
    }

    private PathWithInfo extractWorldZip(CurseForgeMod modInfo, Path zipPath, Path worldsDir) {
        if (levelFrom != LevelFrom.WORLD_FILE) {
            return new PathWithInfo(zipPath);
        }

        // Minecraft server's level property is basically a relative file path
        final String levelName = outputDir.relativize(worldsDir.resolve(modInfo.getSlug())).toString();
        final Path worldDir = worldsDir.resolve(modInfo.getSlug());
        if (!Files.exists(worldDir)) {
            try {
                Files.createDirectories(worldDir);
            } catch (IOException e) {
                throw new GenericException("Unable to create world directory", e);
            }

            log.debug("Unzipping world from {} into {}", zipPath, worldDir);
            try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {

                ZipEntry nextEntry = zipInputStream.getNextEntry();

                if (nextEntry == null || !nextEntry.isDirectory()) {
                    throw new GenericException("Expected top-level directory in world zip " + zipPath);
                }

                // Will replace top diretory with slug name
                final int prefixLength = nextEntry.getName().length();

                while ((nextEntry = zipInputStream.getNextEntry()) != null) {
                    final Path destPath = worldDir.resolve(nextEntry.getName().substring(prefixLength));
                    if (nextEntry.isDirectory()) {
                        Files.createDirectory(destPath);
                    }
                    else {
                        Files.copy(zipInputStream, destPath);
                    }
                }

            } catch (IOException e) {
                throw new GenericException("Failed to open world zip included with modpack", e);
            }
        }
        else {
            log.debug("Extracted world directory '{}' already exists for {}", worldDir, modInfo.getSlug());
        }

        return new PathWithInfo(zipPath)
            .setLevelName(levelName);

    }

    private boolean isServerMod(CurseForgeFile file) {
        /*
            Rules:
            - if marked server, instant winner
            - if marked client, keep looking since it might also be marked server
            - if not marked client (nor server) by end, it's a library so also wins
         */

        boolean client = false;
        for (final String entry : file.getGameVersions()) {
            if (entry.equalsIgnoreCase("server")) {
                return true;
            }
            if (entry.equalsIgnoreCase("client")) {
                client = true;
            }
        }
        return !client;
    }

    private void prepareModLoader(String id, String minecraftVersion) {
        log.debug("Preparing mod loader given id={} minecraftVersion={}", id, minecraftVersion);

        // id could be values like
        // neoforge-1.20.1-47.1.99
        final String[] parts = id.split("-", 3);
        if (parts.length < 2) {
            throw new GenericException("Unknown modloader ID: " + id);
        }

        final String loaderVersion = parts.length == 2 ? parts[1] : parts[2];

        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-curseforge", sharedFetchOptions)) {

            switch (parts[0]) {
                case "forge":
                    prepareForge(sharedFetch, minecraftVersion, loaderVersion);
                    break;

                case "neoforge":
                    prepareNeoForge(sharedFetch, minecraftVersion, loaderVersion);
                    break;

                case "fabric":
                    prepareFabric(minecraftVersion, loaderVersion);
                    break;

                default:
                    throw new InvalidParameterException(String.format("ModLoader %s is not yet supported", parts[0]));
            }
        }
    }

    private void prepareFabric(String minecraftVersion, String loaderVersion) {
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(outputDir)
            .setResultsFile(resultsFile)
            .setForceReinstall(forceReinstallModloader);
        installer.installUsingVersions(minecraftVersion, loaderVersion, null);
    }

    private void prepareForge(SharedFetch sharedFetch, String minecraftVersion, String loaderVersion) {
        new ForgeInstaller(
            new ForgeInstallerResolver(sharedFetch,
                minecraftVersion, loaderVersion
            )
        )
            .install(outputDir, resultsFile, forceReinstallModloader, "Forge");
    }

    private void prepareNeoForge(SharedFetch sharedFetch, String minecraftVersion, String loaderVersion) {
        new ForgeInstaller(
            new NeoForgeInstallerResolver(sharedFetch,
                minecraftVersion, loaderVersion
            )
        )
            .install(outputDir, resultsFile, forceReinstallModloader, "NeoForge");
    }

    private MinecraftModpackManifest extractModpackManifest(Path modpackZip) throws IOException {
        try (ZipFile zipFile = ZipFile.builder().setPath(modpackZip).get()) {
            final ZipArchiveEntry entry = zipFile.getEntry("manifest.json");
            if (entry != null) {
                try (InputStream in = zipFile.getInputStream(entry)) {
                    return ObjectMappers.defaultMapper().readValue(in, MinecraftModpackManifest.class);
                }
            }
        }
        throw new InvalidParameterException(
            "Modpack file is missing a manifest. Make sure to reference a client modpack file."
        );
    }

}
