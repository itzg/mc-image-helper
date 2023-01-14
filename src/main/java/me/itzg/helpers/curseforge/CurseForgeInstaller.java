package me.itzg.helpers.curseforge;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static me.itzg.helpers.curseforge.MoreCollections.safeStreamFrom;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.curseforge.model.GetModFileResponse;
import me.itzg.helpers.curseforge.model.GetModFilesResponse;
import me.itzg.helpers.curseforge.model.ManifestFileRef;
import me.itzg.helpers.curseforge.model.MinecraftModpackManifest;
import me.itzg.helpers.curseforge.model.ModLoader;
import me.itzg.helpers.curseforge.model.ModsSearchResponse;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.Manifests;
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
                processMod(preparedFetch, uriBuilder, searchResponse.getData().get(0), fileId, fileMatcher);
            }
        }
    }

    private void processMod(SharedFetch preparedFetch, UriBuilder uriBuilder, CurseForgeMod mod, Integer fileId,
        String fileMatcher
    )
        throws IOException {

        final CurseForgeFile modFile;

        if (fileId == null) {
            modFile = resolveModpackFile(preparedFetch, uriBuilder, mod, fileMatcher);
        }
        else {
            modFile = getModFileInfo(preparedFetch, uriBuilder, mod.getId(), fileId);
        }

        final CurseForgeManifest manifest = Manifests.load(outputDir, CURSEFORGE_ID, CurseForgeManifest.class);

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
                return;
            }
            else {
                log.warn("Some files from modpack file {} were missing. Proceeding with a re-install", modFile.getFileName());
            }
        }

        log.info("Processing modpack '{}' ({}) @ {}:{}", modFile.getDisplayName(),
            mod.getSlug(), modFile.getModId(), modFile.getId());
        final List<Path> installedFiles =
            downloadAndProcessModpack(
                preparedFetch, uriBuilder,
                normalizeDownloadUrl(modFile.getDownloadUrl()),
                mod.getSlug()
            );

        final CurseForgeManifest newManifest = CurseForgeManifest.builder()
            .modId(modFile.getModId())
            .fileId(modFile.getId())
            .files(Manifests.relativizeAll(outputDir, installedFiles))
            .build();

        Manifests.cleanup(outputDir, manifest, newManifest, f -> log.info("Removing old file {}", f));

        Manifests.save(outputDir, CURSEFORGE_ID, newManifest);
    }

    private static CurseForgeFile resolveModpackFile(SharedFetch preparedFetch, UriBuilder uriBuilder,
        CurseForgeMod mod,
        String fileMatcher
    ) throws IOException {
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

    private List<Path> downloadAndProcessModpack(
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

    private List<Path> processModpackZip(
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

        final Path modsDir = Files.createDirectories(outputDir.resolve("mods"));

        final List<Path> modFiles = Flux.fromIterable(modpackManifest.getFiles())
            .parallel(parallelism)
            .runOn(Schedulers.newParallel("downloader"))
            .filter(ManifestFileRef::isRequired)
            .filter(manifestFileRef -> !excludeIncludeIds.getExcludeIds().contains(manifestFileRef.getProjectID()))
            .flatMap(fileRef ->
                downloadModFile(preparedFetch, uriBuilder, modsDir,
                    fileRef.getProjectID(), fileRef.getFileID(),
                    excludeIncludeIds.getForceIncludeIds()
                )
            )
            .sequential()
            .collectList()
            .block();

        final List<Path> overrides = applyOverrides(modpackZip, modpackManifest.getOverrides());

        prepareModLoader(modLoader.getId(), modpackManifest.getMinecraft().getVersion());

        return Stream.concat(
                modFiles != null ? modFiles.stream() : Stream.empty(),
                overrides.stream()
            )
            .collect(Collectors.toList());
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
            .flatMap(s -> {
                try {
                    final int id = Integer.parseInt(s);
                    return Mono.just(id);
                } catch (NumberFormatException e) {
                    return slugToId(preparedFetch, uriBuilder, s);
                }
            })
            .collect(Collectors.toSet());
    }

    private Mono<Integer> slugToId(SharedFetch preparedFetch, UriBuilder uriBuilder, String slug) {
        try {
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
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    private List<Path> applyOverrides(Path modpackZip, String overridesDir) throws IOException {
        final ArrayList<Path> overrides = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(modpackZip))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    if (entry.getName().startsWith(overridesDir + "/")) {
                        final String subpath = entry.getName().substring(overridesDir.length() + 1/*for slash*/);
                        final Path outPath = outputDir.resolve(subpath);
                        log.debug("Applying override {}", subpath);

                        if (!Files.exists(outPath)) {
                            Files.createDirectories(outPath.getParent());
                            Files.copy(zip, outPath);
                        }
                        overrides.add(outPath);
                    }
                }
            }
        }
        return overrides;
    }

    private Mono<Path> downloadModFile(SharedFetch preparedFetch, UriBuilder uriBuilder, Path modsDir, int projectID, int fileID,
        Set<Integer> forceIncludeIds
    ) {
        try {
            final CurseForgeFile file = getModFileInfo(preparedFetch, uriBuilder, projectID, fileID);
            log.debug("Download/confirm mod {} @ {}:{}",
                // several mods have non-descriptive display names, like "v1.0.0", so filename tends to be better
                file.getFileName(),
                projectID, fileID
            );
            if (!forceIncludeIds.contains(projectID) && !isServerMod(file)) {
                log.debug("Skipping {} since it is a client mod", file.getFileName());
                return Mono.empty();
            }

            return preparedFetch.fetch(
                    normalizeDownloadUrl(file.getDownloadUrl())
                )
                .toFile(modsDir.resolve(file.getFileName()))
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
        } catch (IOException e) {
            throw new GenericException(String.format("Failed to locate mod file modId=%s fileId=%d", projectID, fileID), e);
        }
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

    private static CurseForgeFile getModFileInfo(SharedFetch preparedFetch, UriBuilder uriBuilder,
        int projectID, int fileID
    ) throws IOException {
        log.debug("Getting mod file metadata for {}:{}", projectID, fileID);

        final GetModFileResponse resp = preparedFetch.fetch(
                uriBuilder.resolve("/mods/{modId}/files/{fileId}", projectID, fileID)
            )
            .toObject(GetModFileResponse.class)
            .execute();

        return resp.getData();
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
