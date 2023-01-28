package me.itzg.helpers.curseforge;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static me.itzg.helpers.curseforge.MoreCollections.safeStreamFrom;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes;
import me.itzg.helpers.curseforge.model.Category;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.curseforge.model.GetCategoriesResponse;
import me.itzg.helpers.curseforge.model.GetModFileResponse;
import me.itzg.helpers.curseforge.model.GetModFilesResponse;
import me.itzg.helpers.curseforge.model.GetModResponse;
import me.itzg.helpers.curseforge.model.ManifestFileRef;
import me.itzg.helpers.curseforge.model.MinecraftModpackManifest;
import me.itzg.helpers.curseforge.model.ModLoader;
import me.itzg.helpers.curseforge.model.ModsSearchResponse;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.json.ObjectMappers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@Slf4j
public class CurseForgeInstaller {

    private static final String MINECRAFT_GAME_ID = "432";
    public static final String CURSEFORGE_ID = "curseforge";
    public static final String LEVEL_DAT_SUFFIX = "/level.dat";
    public static final int LEVEL_DAT_SUFFIX_LEN = LEVEL_DAT_SUFFIX.length();

    private final Path outputDir;
    private final Path resultsFile;

    @Getter
    @Setter
    private String apiBaseUrl = "https://api.curse.tools/v1/cf";
    @Getter
    @Setter
    private int parallelism = 4;

    @Getter
    @Setter
    private boolean forceSynchronize;

    @Getter
    @Setter
    private Set<String> forceIncludeMods = emptySet();

    @Getter
    @Setter
    private Set<String> excludedModIds = emptySet();

    @Getter @Setter
    private ExcludeIncludesContent excludeIncludes;

    @Getter @Setter
    private LevelFrom levelFrom;

    @Getter @Setter
    private boolean overridesSkipExisting;

    private final Set<String> applicableClassIdSlugs = new HashSet<>(Arrays.asList(
        "mc-mods",
        "bukkit-plugins",
        "worlds"
    ));

    public void install(String slug, String fileMatcher, Integer fileId) throws IOException {
        requireNonNull(outputDir, "outputDir is required");
        requireNonNull(slug, "slug is required");

        final UriBuilder uriBuilder = UriBuilder.withBaseUrl(apiBaseUrl);

        try (SharedFetch preparedFetch = Fetch.sharedFetch("install-curseforge")) {
            // TODO encapsulate preparedFetch and uriBuilder to avoid passing deep into call tree

            final ModsSearchResponse searchResponse = preparedFetch.fetch(
                    uriBuilder.resolve("/mods/search?gameId={gameId}&slug={slug}", MINECRAFT_GAME_ID, slug)
                )
                .toObject(ModsSearchResponse.class)
                .execute();

            if (searchResponse.getData() == null || searchResponse.getData().isEmpty()) {
                throw new GenericException("No mods found with slug={}" + slug);
            }
            else if (searchResponse.getData().size() > 1) {
                throw new GenericException("More than one mod found with slug={}" + slug);
            }
            else {
                processModPack(preparedFetch, uriBuilder, searchResponse.getData().get(0), fileId, fileMatcher);
            }
        }
    }

    private void processModPack(SharedFetch preparedFetch, UriBuilder uriBuilder, CurseForgeMod mod, Integer fileId,
        String fileMatcher
    )
        throws IOException {

        final CurseForgeFile modFile;

        if (fileId == null) {
            modFile = resolveModpackFile(preparedFetch, uriBuilder, mod, fileMatcher);
        }
        else {
            modFile = getModFileInfo(preparedFetch, uriBuilder, mod.getId(), fileId)
                .switchIfEmpty(Mono.error(() -> new GenericException("Unable to resolve modpack's file")))
                .block();
        }

        final CurseForgeManifest manifest = Manifests.load(outputDir, CURSEFORGE_ID, CurseForgeManifest.class);
        // to adapt to previous copies of manifest
        trimLevelsContent(manifest);

        //noinspection DataFlowIssue handled by switchIfEmpty
        if (manifest != null
            && manifest.getFileId() == modFile.getId()
            && manifest.getModId() == modFile.getModId()
        ) {
            if (forceSynchronize) {
                log.info("Requested force synchronize of {}", modFile.getDisplayName());
            }
            else if (Manifests.allFilesPresent(outputDir, manifest)) {
                log.info("Requested CurseForge modpack {} is already installed for {}",
                    modFile.getDisplayName(), mod.getName()
                );

                // Double-check the mod loader is still present and ready
                if (manifest.getMinecraftVersion() != null && manifest.getModLoaderId() != null) {
                    prepareModLoader(manifest.getModLoaderId(), manifest.getMinecraftVersion());
                }

                // ...and write out level name from previous run
                if (resultsFile != null && manifest.getLevelName() != null) {
                    try (ResultsFileWriter resultsFileWriter = new ResultsFileWriter(resultsFile, true)) {
                        resultsFileWriter.write("LEVEL", manifest.getLevelName());
                    }
                }

                return;
            }
            else {
                log.warn("Some files from modpack file {} were missing. Proceeding with a re-install", modFile.getFileName());
            }
        }

        //noinspection DataFlowIssue handled by switchIfEmpty
        log.info("Processing modpack '{}' ({}) @ {}:{}", modFile.getDisplayName(),
            mod.getSlug(), modFile.getModId(), modFile.getId());
        final ModPackResults results =
            downloadAndProcessModpackZip(
                preparedFetch, uriBuilder,
                normalizeDownloadUrl(modFile.getDownloadUrl()),
                mod.getSlug()
            );

        final CurseForgeManifest newManifest = CurseForgeManifest.builder()
            .modId(modFile.getModId())
            .fileId(modFile.getId())
            .files(Manifests.relativizeAll(outputDir, results.getFiles()))
            .minecraftVersion(results.getMinecraftVersion())
            .modLoaderId(results.getModLoaderId())
            .levelName(results.getLevelName())
            .build();

        Manifests.cleanup(outputDir, manifest, newManifest, f -> log.info("Removing old file {}", f));

        Manifests.save(outputDir, CURSEFORGE_ID, newManifest);

        if (resultsFile != null && results.getLevelName() != null) {
            try (ResultsFileWriter resultsFileWriter = new ResultsFileWriter(resultsFile, true)) {
                resultsFileWriter.write("LEVEL", results.getLevelName());
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

    private static CurseForgeFile resolveModpackFile(SharedFetch preparedFetch, UriBuilder uriBuilder,
        CurseForgeMod mod,
        String fileMatcher
    ) {
        // NOTE latestFiles in mod is only one or two files, so retrieve the full list instead
        final GetModFilesResponse resp = preparedFetch.fetch(
                uriBuilder.resolve("/mods/{modId}/files", mod.getId())
            )
            .toObject(GetModFilesResponse.class)
            .execute();

        return resp.getData().stream()
            .filter(file ->
                // even though we're preparing a server, we need client modpack to get deterministic manifest layout
                !file.isServerPack() &&
                    (fileMatcher == null || file.getFileName().contains(fileMatcher)))
            .findFirst()
            .orElseThrow(() -> {
                log.debug("No matching files trying fileMatcher={} against {}", fileMatcher,
                    mod.getLatestFiles()
                );
                return new GenericException("No matching files found for mod");
            });
    }

    private ModPackResults downloadAndProcessModpackZip(
        SharedFetch preparedFetch, UriBuilder uriBuilder, URI downloadUrl, String modpackSlug
    )
        throws IOException {
        final Path modpackZip = Files.createTempFile("curseforge-modpack", "zip");

        try {
            preparedFetch.fetch(downloadUrl)
                .toFile(modpackZip)
                .handleStatus((status, uri, file) ->
                    log.debug("Modpack file retrieval: status={} uri={} file={}", status, uri, file)
                )
                .execute();

            return processModpackZip(preparedFetch, uriBuilder, modpackZip, modpackSlug);
        } finally {
            Files.delete(modpackZip);
        }
    }

    private ModPackResults processModpackZip(
        SharedFetch preparedFetch, UriBuilder uriBuilder, Path modpackZip, String modpackSlug
    )
        throws IOException {

        final ExcludeIncludeIds excludeIncludeIds = resolveExcludeIncludes(preparedFetch, uriBuilder, modpackSlug);
        log.debug("Using {}", excludeIncludeIds);

        final MinecraftModpackManifest modpackManifest = extractModpackManifest(modpackZip);

        final ModLoader modLoader = modpackManifest.getMinecraft().getModLoaders().stream()
            .filter(ModLoader::isPrimary)
            .findFirst()
            .orElseThrow(() -> new GenericException("Unable to find primary mod loader in modpack"));

        final OutputPaths outputPaths = new OutputPaths(
            Files.createDirectories(outputDir.resolve("mods")),
            Files.createDirectories(outputDir.resolve("plugins")),
            Files.createDirectories(outputDir.resolve("saves"))
        );

        final Map<Integer /*classId*/, Category> categoryClasses = loadCategoryClasses(preparedFetch, uriBuilder);

        // Go through all the files listed in modpack (given project ID + file ID)
        final List<PathWithInfo> modFiles = Flux.fromIterable(modpackManifest.getFiles())
            // ...do parallel downloads to let small ones make progress during big ones
            .parallel(parallelism)
            .runOn(Schedulers.newParallel("downloader"))
            // ...does the modpack even say it's required?
            .filter(ManifestFileRef::isRequired)
            // ...is this mod file excluded because it is a client mod that didn't declare as such
            .filter(manifestFileRef -> !excludeIncludeIds.getExcludeIds().contains(manifestFileRef.getProjectID()))
            // ...download and possibly unzip world file
            .flatMap(fileRef ->
                downloadModFile(preparedFetch, uriBuilder, outputPaths,
                    fileRef.getProjectID(), fileRef.getFileID(),
                    excludeIncludeIds.getForceIncludeIds(),
                    categoryClasses
                )
            )
            .sequential()
            .collectList()
            .block();

        final OverridesResult overridesResult = applyOverrides(modpackZip, modpackManifest.getOverrides());

        prepareModLoader(modLoader.getId(), modpackManifest.getMinecraft().getVersion());

        return new ModPackResults()
            .setFiles(Stream.concat(
                        modFiles != null ? modFiles.stream().map(PathWithInfo::getPath) : Stream.empty(),
                        overridesResult.paths.stream()
                    )
                    .collect(Collectors.toList())
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

    private ExcludeIncludeIds resolveExcludeIncludes(SharedFetch preparedFetch, UriBuilder uriBuilder,
        String modpackSlug
    ) {
        log.debug("Reconciling exclude/includes from given {}", excludeIncludes);

        if (excludeIncludes == null) {
            return new ExcludeIncludeIds(emptySet(), emptySet());
        }

        final ExcludeIncludes specific =
            excludeIncludes.getModpacks() != null ? excludeIncludes.getModpacks().get(modpackSlug) : null;

        return Mono.zip(
                resolveFromSlugOrIds(
                    preparedFetch, uriBuilder,
                    excludeIncludes.getGlobalExcludes(),
                    specific != null ? specific.getExcludes() : null
                ),
                resolveFromSlugOrIds(
                    preparedFetch, uriBuilder,
                    excludeIncludes.getGlobalForceIncludes(),
                    specific != null ? specific.getForceIncludes() : null
                )
            )
            .map(tuple ->
                new ExcludeIncludeIds(tuple.getT1(), tuple.getT2())
            )
            .block();
    }

    private Mono<Set<Integer>> resolveFromSlugOrIds(SharedFetch preparedFetch, UriBuilder uriBuilder,
        Collection<String> global, Collection<String> specific
    ) {
        log.trace("Resolving slug|id into IDs global={} specific={}", global, specific);

        return Flux.fromStream(
                Stream.concat(
                    safeStreamFrom(global), safeStreamFrom(specific)
                )
            )
            .parallel(parallelism)
            .flatMap(s -> {
                try {
                    final int id = Integer.parseInt(s);
                    return Mono.just(id);
                } catch (NumberFormatException e) {
                    return slugToId(preparedFetch, uriBuilder, s);
                }
            })
            .sequential()
            .collect(Collectors.toSet());
    }

    private Mono<Integer> slugToId(SharedFetch preparedFetch, UriBuilder uriBuilder, String slug) {
        return preparedFetch
            .fetch(
                uriBuilder.resolve("/mods/search?gameId={gameId}&slug={slug}", MINECRAFT_GAME_ID, slug)
            )
            .toObject(ModsSearchResponse.class)
            .assemble()
            .flatMap(resp ->
                resp.getData() == null || resp.getData().isEmpty() ?
                    Mono.error(new GenericException("Unable to resolve slug into ID (no matches): "+slug))
                    : resp.getData().size() > 1 ?
                        Mono.error(new GenericException("Unable to resolve slug into ID (multiple): "+slug))
                        : Mono.just(resp.getData().get(0).getId())
                );
    }

    @AllArgsConstructor
    static class OverridesResult {
        List<Path> paths;
        String levelName;
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
                            log.debug("Applying override {}", subpath);
                            // zip files don't always list the directories before the files, so just create-as-needed
                            Files.createDirectories(outPath.getParent());
                            Files.copy(zip, outPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        else {
                            log.debug("Skipping override={} since the file already existed", subpath);
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

    private Mono<PathWithInfo> downloadModFile(SharedFetch preparedFetch, UriBuilder uriBuilder, OutputPaths outputPaths, int projectID, int fileID,
        Set<Integer> forceIncludeIds,
        Map<Integer, Category> categoryClasses
    ) {
        return getModInfo(preparedFetch, uriBuilder, projectID)
            .flatMap(modInfo -> {
                final Category category = categoryClasses.get(modInfo.getClassId());
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

                return getModFileInfo(preparedFetch, uriBuilder, projectID, fileID)
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

                        final Mono<Path> assembledDownload = preparedFetch.fetch(
                                normalizeDownloadUrl(cfFile.getDownloadUrl())
                            )
                            .toFile(baseDir.resolve(cfFile.getFileName()))
                            .skipExisting(true)
                            .handleStatus((status, uri, f) -> {
                                switch (status) {
                                    case SKIP_FILE_EXISTS:
                                        log.info("Mod file {} already exists", outputDir.relativize(f));
                                        break;
                                    case DOWNLOADED:
                                        log.info("Downloaded mod file {}", outputDir.relativize(f));
                                        break;
                                }
                            })
                            .assemble();

                        return isWorld ?
                            assembledDownload
                                .map(path -> extractWorldZip(modInfo, path, outputPaths.getWorldsDir()))
                            : assembledDownload
                                .map(PathWithInfo::new);
                    });
            });
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

    private static Mono<CurseForgeFile> getModFileInfo(SharedFetch preparedFetch, UriBuilder uriBuilder,
        int projectID, int fileID
    ) {
        log.debug("Getting mod file metadata for {}:{}", projectID, fileID);

        return preparedFetch.fetch(
                uriBuilder.resolve("/mods/{modId}/files/{fileId}", projectID, fileID)
            )
            .toObject(GetModFileResponse.class)
            .assemble()
            .map(GetModFileResponse::getData);
    }

    private static Mono<CurseForgeMod> getModInfo(
        SharedFetch preparedFetch, UriBuilder uriBuilder,
        int projectID
    ) {
        log.debug("Getting mod metadata for {}", projectID);

        return preparedFetch.fetch(
            uriBuilder.resolve("/mods/{modId}", projectID)
        )
            .toObject(GetModResponse.class)
            .assemble()
            .map(GetModResponse::getData);
    }

    private void prepareModLoader(String id, String minecraftVersion) throws IOException {
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

    private void prepareFabric(String minecraftVersion, String loaderVersion) throws IOException {
        final FabricLauncherInstaller installer = new FabricLauncherInstaller(outputDir, resultsFile);
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

            throw new GenericException("Modpack is missing manifest.json");
        }
    }

    /**
     * @return mapping of classId to category instances that are classes and an acceptable server-side type
     */
    private Map<Integer, Category> loadCategoryClasses(SharedFetch preparedFetch, UriBuilder uriBuilder) {
        return preparedFetch
            // get only categories that are classes, like mc-mods
            .fetch(uriBuilder.resolve("/categories?gameId={gameId}&classesOnly=true", MINECRAFT_GAME_ID))
            .toObject(GetCategoriesResponse.class)
            .assemble()
            .map(resp ->
                resp.getData().stream()
                    // only keep the specific classes we want: mods, plugins, worlds
                    .filter(category -> applicableClassIdSlugs.contains(category.getSlug()))
                    .collect(Collectors.toMap(
                        // ...and enable quick lookup from "classId" in mod file metadata
                        Category::getId,
                        Function.identity()
                    ))
                )
            .block();
    }

    private static URI normalizeDownloadUrl(String downloadUrl) {
        final int nameStart = downloadUrl.lastIndexOf('/');

        final String filename = downloadUrl.substring(nameStart + 1);
        return URI.create(
            downloadUrl.substring(0, nameStart + 1) +
                filename
                    .replace(" ", "%20")
                    .replace("[", "%5B")
                    .replace("]", "%5D")
        );
    }
}
