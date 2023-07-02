package me.itzg.helpers.curseforge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes;
import me.itzg.helpers.curseforge.model.*;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.json.ObjectMappers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static me.itzg.helpers.singles.MoreCollections.safeStreamFrom;

@RequiredArgsConstructor
@Slf4j
public class CurseForgeInstaller {

    public static final String API_KEY_VAR = "CF_API_KEY";
    public static final String MODPACK_ZIP_VAR = "CF_MODPACK_ZIP";

    public static final String ETERNAL_DEVELOPER_CONSOLE_URL = "https://console.curseforge.com/";
    private static final String MINECRAFT_GAME_ID = "432";
    public static final String CURSEFORGE_ID = "curseforge";
    public static final String LEVEL_DAT_SUFFIX = "/level.dat";
    public static final int LEVEL_DAT_SUFFIX_LEN = LEVEL_DAT_SUFFIX.length();
    public static final String CATEGORY_SLUG_MODPACKS = "modpacks";
    public static final String REPO_SUBDIR_MODPACKS = "modpacks";
    public static final String REPO_SUBDIR_MODS = "mods";
    public static final String REPO_SUBDIR_WORLDS = "worlds";

    private final Path outputDir;
    private final Path resultsFile;

    @Getter @Setter
    private String apiBaseUrl = "https://api.curseforge.com/v1";

    @Getter @Setter
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
        "mc-mods",
        "bukkit-plugins",
        "worlds"
    ));

    public void installFromModpackZip(Path modpackZip, String slug) throws IOException {
        requireNonNull(modpackZip, "modpackZip is required");

        install(slug, context -> {
            final MinecraftModpackManifest modpackManifest = extractModpackManifest(modpackZip);

            processModpackManifest(context, modpackManifest,
                () -> applyOverrides(modpackZip, modpackManifest.getOverrides())
                );
        });
    }

    public void installFromModpackManifest(Path modpackManifestPath, String slug) throws IOException {
        requireNonNull(modpackManifestPath, "modpackManifest is required");

        install(slug, context -> {
            final MinecraftModpackManifest modpackManifest = ObjectMappers.defaultMapper()
                .readValue(modpackManifestPath.toFile(), MinecraftModpackManifest.class);

            processModpackManifest(context, modpackManifest,
                () -> new OverridesResult(Collections.emptyList(), null)
                );
        });
    }

    public void install(String slug, String fileMatcher, Integer fileId) throws IOException {

        install(slug, context ->
            installByRetrievingModpackZip(context, fileMatcher, fileId)
        );
    }

    protected void install(String slug, InstallationEntryPoint entryPoint) throws IOException {
        requireNonNull(outputDir, "outputDir is required");
        requireNonNull(slug);
        requireNonNull(entryPoint);

        final CurseForgeManifest manifest = Manifests.load(outputDir, CURSEFORGE_ID, CurseForgeManifest.class);
        // to adapt to previous copies of manifest
        trimLevelsContent(manifest);

        if (apiKey == null || apiKey.isEmpty()) {
            if (manifest != null) {
                log.warn("API key is not set, so will re-use previous modpack installation of {}",
                    manifest.getSlug() != null ? manifest.getSlug() : "Project ID "+manifest.getModId());
                log.warn("Obtain an API key from " + ETERNAL_DEVELOPER_CONSOLE_URL
                    + " and set the environment variable "+ API_KEY_VAR +" in order to restore full functionality.");
                return;
            }
            else {
                throw new InvalidParameterException("API key is not set. Obtain an API key from " + ETERNAL_DEVELOPER_CONSOLE_URL
                    + " and set the environment variable "+ API_KEY_VAR);
            }
        }

        try (
            CurseForgeApiClient cfApi = new CurseForgeApiClient(
                apiBaseUrl, apiKey, sharedFetchOptions,
                MINECRAFT_GAME_ID
            )
        ) {
            final CategoryInfo categoryInfo = cfApi.loadCategoryInfo(applicableClassIdSlugs, CATEGORY_SLUG_MODPACKS);

            entryPoint.install(
                new InstallContext(slug, cfApi, categoryInfo, manifest)
            );

        } catch (FailedRequestException e) {
            if (e.getStatusCode() == 403) {
                throw new InvalidParameterException(String.format("Access to %s is forbidden. Make sure to set %s to a valid API key from %s",
                        apiBaseUrl, API_KEY_VAR, ETERNAL_DEVELOPER_CONSOLE_URL
                ), e);
            } else {
                throw e;
            }
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
        final CurseForgeMod curseForgeMod = context.cfApi.searchMod(context.slug, context.categoryInfo);

        resolveModpackFileAndProcess(context, curseForgeMod, fileId, fileMatcher);
    }

    private void processModpackManifest(InstallContext context,
        MinecraftModpackManifest modpackManifest, OverridesApplier overridesApplier
    ) throws IOException {

        final String modpackName = modpackManifest.getName();
        final String modpackVersion = modpackManifest.getVersion();
        // absolute value, so negative IDs don't look weird
        final int pseudoModId = Math.abs(modpackName.hashCode());
        final int pseudoFileId = Math.abs(hashModpackFileReferences(modpackManifest.getFiles()));

        if (matchesPreviousInstall(context, pseudoModId, pseudoFileId)
        ) {
            if (forceSynchronize) {
                log.info("Requested force synchronize of {}", modpackName);
            }
            else if (Manifests.allFilesPresent(outputDir, context.prevInstallManifest)) {
                log.info("Requested CurseForge modpack {} is already installed", modpackName);

                finalizeExistingInstallation(context.prevInstallManifest);

                return;
            }
            else {
                log.warn("Some files from modpack file {} were missing. Proceeding with a re-install", modpackName);
            }
        }

        log.info("Installing modpack '{}' version {} from provided modpack zip",
            modpackName, modpackVersion);

        final ModPackResults results = processModpack(context, modpackManifest, overridesApplier);

        finalizeResults(context, results,
            pseudoModId, pseudoFileId, results.getName());
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
            else if (Manifests.allFilesPresent(outputDir, context.prevInstallManifest)) {
                log.info("Requested CurseForge modpack {} is already installed for {}",
                    modFile.getDisplayName(), mod.getName()
                );

                finalizeExistingInstallation(context.prevInstallManifest);

                return;
            }
            else {
                log.warn("Some files from modpack file {} were missing. Proceeding with a re-install", modFile.getFileName());
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
            modpackZip = context.cfApi.downloadTemp(modFile, "zip",
                    (status, uri, file) ->
                        log.debug("Modpack file retrieval: status={} uri={} file={}", status, uri, file)
                )
                .block();
        }

        log.info("Processing modpack '{}' ({}) @ {}:{}", modFile.getDisplayName(),
            mod.getSlug(), modFile.getModId(), modFile.getId());

        if (modpackZip == null) {
            throw new GenericException("Download of modpack zip was empty");
        }

        /*downloadModpackZip(context, modFile);*/
        final ModPackResults results;
        try {

            final MinecraftModpackManifest modpackManifest = extractModpackManifest(modpackZip);
            results = processModpack(context, modpackManifest, () -> applyOverrides(modpackZip,
                modpackManifest.getOverrides()));
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

    private static Path locateFileIn(String fileName, Path... dirs) {
        for (Path dir : dirs) {
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
            }
        }
    }

    private void finalizeResults(InstallContext context, ModPackResults results, int modId, int fileId, String displayName) throws IOException {
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

        final Path needsDownloadFile = outputDir.resolve("MODS_NEED_DOWNLOAD.txt");
        if (!results.getNeedsDownload().isEmpty()) {
            try (BufferedWriter writer = Files.newBufferedWriter(needsDownloadFile)) {
                for (PathWithInfo info : results.getNeedsDownload()) {
                    writer.write(String.format("%s :: \"%s\" FROM %s",
                        info.getModInfo().getName(),
                        info.getCurseForgeFile().getDisplayName(),
                        info.getModInfo().getLinks().getWebsiteUrl()
                        ));
                    writer.newLine();
                }
            }
        }
        else {
            Files.deleteIfExists(needsDownloadFile);
        }

        if (resultsFile != null) {
            try (ResultsFileWriter resultsFileWriter = new ResultsFileWriter(resultsFile, true)) {
                if (results.getLevelName() != null) {
                    resultsFileWriter.write("LEVEL", results.getLevelName());
                }
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

    /**
     * @param overridesApplier typically calls {@link #applyOverrides(Path, String)}
     */
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
                downloadFileFromModpack(context, outputPaths,
                    fileRef.getProjectID(), fileRef.getFileID(),
                    excludeIncludeIds.getForceIncludeIds(),
                    context.categoryInfo
                )
            )
            .collectList()
            .block();

        final OverridesResult overridesResult = overridesApplier.apply();

        prepareModLoader(modLoader.getId(), modpackManifest.getMinecraft().getVersion());

        return buildResults(modpackManifest, modLoader, modFiles, overridesResult);
    }

    private ModPackResults buildResults(MinecraftModpackManifest modpackManifest, ModLoader modLoader, List<PathWithInfo> modFiles, OverridesResult overridesResult) {
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

    private String resolveLevelName(List<PathWithInfo> modFiles, OverridesResult overridesResult) {
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

    @AllArgsConstructor
    static class OverridesResult {
        List<Path> paths;
        String levelName;
    }

    interface OverridesApplier {
        OverridesResult apply() throws IOException;
    }

    private OverridesResult applyOverrides(Path modpackZip, String overridesDir) throws IOException {
        log.debug("Applying overrides from '{}' in zip file", overridesDir);

        final String levelEntryName = findLevelEntryInOverrides(modpackZip, overridesDir);
        final String levelEntryNamePrefix = levelEntryName != null ? levelEntryName+"/" : null;

        final boolean worldOutputDirExists = levelEntryName != null &&
            Files.exists(outputDir.resolve(levelEntryName));

        log.debug("While applying overrides, found level entry='{}' in modpack overrides and worldOutputDirExists={}",
            levelEntryName, worldOutputDirExists);

        final String overridesDirPrefix = overridesDir + "/";
        final int overridesPrefixLen = overridesDirPrefix.length();

        final List<Path> overrides = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(modpackZip))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().startsWith(overridesDirPrefix)) {
                    if (!entry.isDirectory()) {
                        if (log.isTraceEnabled()) {
                            log.trace("Processing override entry={}:{}", entry.isDirectory() ? "D":"F", entry.getName());
                        }
                        final String subpath = entry.getName().substring(overridesPrefixLen);
                        final Path outPath = outputDir.resolve(subpath);

                        // Rules
                        // - don't ever overwrite world data
                        // - user has option to not overwrite any existing file from overrides
                        // - otherwise user will want latest modpack's overrides content

                        final boolean isInWorldDirectory = levelEntryNamePrefix != null &&
                            subpath.startsWith(levelEntryNamePrefix);

                        if (worldOutputDirExists && isInWorldDirectory) {
                            continue;
                        }

                        if ( !(overridesSkipExisting && Files.exists(outPath)) ) {
                            log.trace("Applying override {}", subpath);
                            // zip files don't always list the directories before the files, so just create-as-needed
                            Files.createDirectories(outPath.getParent());
                            Files.copy(zip, outPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        else {
                            log.trace("Skipping override={} since the file already existed", subpath);
                        }

                        // Track this path for later cleanup
                        // UNLESS it is within a world/level directory
                        if (levelEntryName == null || !isInWorldDirectory) {
                            overrides.add(outPath);
                        }
                    }
                }
            }
        }

        return new OverridesResult(overrides,
            levelFrom == LevelFrom.OVERRIDES ? levelEntryName : null
        );
    }

    /**
     * @return if present, the subpath to a world/level directory with the overrides prefix removed otherwise null
     */
    private String findLevelEntryInOverrides(Path modpackZip, String overridesDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(modpackZip))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(overridesDir + "/") && name.endsWith(LEVEL_DAT_SUFFIX)) {
                    return name.substring(overridesDir.length()+1, name.length() - LEVEL_DAT_SUFFIX_LEN);
                }
            }
        }
        return null;
    }

    /**
     * Downloads the referenced project-file into the appropriate subdirectory from outputPaths
     */
    private Mono<PathWithInfo> downloadFileFromModpack(
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

                final Path baseDir;
                final boolean isWorld;
                if (category.getSlug().endsWith("-mods")) {
                    baseDir = outputPaths.getModsDir();
                    isWorld = false;
                }
                else if (category.getSlug().endsWith("-plugins")) {
                    baseDir = outputPaths.getPluginsDir();
                    isWorld = false;
                }
                else if (category.getSlug().equals("worlds")) {
                    baseDir = outputPaths.getWorldsDir();
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
                            resolveToOutputFile(context, modInfo, isWorld, baseDir, cfFile);

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
            });
    }

    @RequiredArgsConstructor
    static class ResolveResult {
        final Path path;
        @Setter
        boolean downloadNeeded;
    }

    private Mono<ResolveResult> resolveToOutputFile(InstallContext context, CurseForgeMod modInfo, boolean isWorld, Path baseDir, CurseForgeFile cfFile) {
        final Path outputFile = baseDir.resolve(cfFile.getFileName());
        if (!isWorld && Files.exists(outputFile)) {
            log.info("Mod file {} already exists", outputDir.relativize(outputFile));
            return Mono.just(new ResolveResult(outputFile));
        }
        else if (cfFile.getDownloadUrl() == null) {
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
                return Mono.just(resolved)
                    .publishOn(Schedulers.boundedElastic())
                    .flatMap(path -> {
                        try {
                            log.info("Mod file {} obtained from downloads repo", outputDir.relativize(outputFile));
                            //noinspection BlockingMethodInNonBlockingContext because IntelliJ is confused
                            return Mono.just(Files.copy(resolved, outputFile));
                        } catch (IOException e) {
                            return Mono.error(new GenericException("Failed to copy file from downloads repo", e));
                        }

                    })
                    .map(ResolveResult::new);
            }
        }
        else {
            return context.cfApi.download(cfFile, outputFile, (status, uri, f) -> {
                switch (status) {
                    case SKIP_FILE_EXISTS:
                        log.info("Mod file {} already exists", outputDir.relativize(f));
                        break;
                    case DOWNLOADED:
                        log.info("Downloaded mod file {}", outputDir.relativize(f));
                        break;
                }
            })
                .map(ResolveResult::new);
        }
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
                    throw new GenericException("Expected top-level directory in world zip "+zipPath);
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
        final String[] parts = id.split("-", 2);
        if (parts.length != 2) {
            throw new GenericException("Unknown modloader ID: " + id);
        }

        switch (parts[0]) {
            case "forge":
                prepareForge(minecraftVersion, parts[1]);
                break;

            case "fabric":
                prepareFabric(minecraftVersion, parts[1]);
                break;
        }
    }

    private void prepareFabric(String minecraftVersion, String loaderVersion) {
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(outputDir)
            .setResultsFile(resultsFile);
        installer.installUsingVersions(minecraftVersion, loaderVersion, null);
    }

    private void prepareForge(String minecraftVersion, String forgeVersion) {
        final ForgeInstaller installer = new ForgeInstaller();
        installer.install(minecraftVersion, forgeVersion, outputDir, resultsFile, false, null);
    }

    private MinecraftModpackManifest extractModpackManifest(Path modpackZip) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(modpackZip))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    return ObjectMappers.defaultMapper()
                        .readValue(zip, MinecraftModpackManifest.class);
                }
            }

            throw new InvalidParameterException(
                "Modpack file is missing a manifest. Make sure to reference a client modpack file."
            );
        }
    }

}
