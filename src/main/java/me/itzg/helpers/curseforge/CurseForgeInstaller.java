package me.itzg.helpers.curseforge;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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

    public void install(String slug, String fileMatcher, Integer fileId, Set<Integer> excludedModIds) throws IOException {
        requireNonNull(outputDir, "outputDir is required");
        requireNonNull(slug, "slug is required");

        final UriBuilder uriBuilder = UriBuilder.withBaseUrl(apiBaseUrl);

        try (SharedFetch preparedFetch = Fetch.sharedFetch("install-curseforge")) {
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
                processMod(preparedFetch, uriBuilder, searchResponse.getData().get(0), fileId, fileMatcher,
                    excludedModIds != null ? excludedModIds : Collections.emptySet()
                );
            }
        }
    }

    private void processMod(SharedFetch preparedFetch, UriBuilder uriBuilder, CurseForgeMod mod, Integer fileId, String fileMatcher,
        Set<Integer> excludedModIds
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
            if (Manifests.allFilesPresent(outputDir, manifest)) {
                log.info("Requested CurseForge modpack {} is already installed for {}",
                    modFile.getDisplayName(), mod.getName()
                );
                return;
            }
            log.warn("Some files from modpack file {} were missing. Proceeding with a re-install", modFile.getFileName());
        }

        log.info("Processing modpack {} @ {}:{}", modFile.getDisplayName(), modFile.getModId(), modFile.getId());
        final List<Path> installedFiles = processModpackFile(preparedFetch, uriBuilder,
            normalizeDownloadUrl(modFile.getDownloadUrl()),
            excludedModIds
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

    private static URI normalizeDownloadUrl(String downloadUrl) {
        final int nameStart = downloadUrl.lastIndexOf('/');

        final String filename = downloadUrl.substring(nameStart + 1);
        return URI.create(
            downloadUrl.substring(0, nameStart+1) +
                filename
                    .replace(" ", "%20")
                    .replace("[", "%5B")
                    .replace("]", "%5D")
            );
    }

    private List<Path> processModpackFile(SharedFetch preparedFetch, UriBuilder uriBuilder, URI downloadUrl,
        Set<Integer> excludedModIds
    )
        throws IOException {
        final Path downloaded = Files.createTempFile("curseforge-modpack", "zip");

        try {
            preparedFetch.fetch(downloadUrl)
                .toFile(downloaded)
                .execute();

            final MinecraftModpackManifest modpackManifest = extractModpackManifest(downloaded);

            final ModLoader modLoader = modpackManifest.getMinecraft().getModLoaders().stream()
                .filter(ModLoader::isPrimary)
                .findFirst()
                .orElseThrow(() -> new GenericException("Unable to find primary mod loader in modpack"));

            final Path modsDir = Files.createDirectories(outputDir.resolve("mods"));

            final List<Path> modFiles = modpackManifest.getFiles().stream()
                .filter(ManifestFileRef::isRequired)
                .filter(manifestFileRef -> !excludedModIds.contains(manifestFileRef.getProjectID()))
                .map(fileRef ->
                    downloadModFile(preparedFetch, uriBuilder, modsDir, fileRef.getProjectID(), fileRef.getFileID())
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            final List<Path> overrides = applyOverrides(downloaded);

            prepareModLoader(modLoader.getId(), modpackManifest.getMinecraft().getVersion());

            return Stream.concat(modFiles.stream(), overrides.stream())
                .collect(Collectors.toList());
        }
        finally {
            Files.delete(downloaded);
        }
    }

    private List<Path> applyOverrides(Path modpackZip) throws IOException {
        final ArrayList<Path> overrides = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(modpackZip))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    // TODO lookup "overrides" from file model
                    if (entry.getName().startsWith("overrides/")) {
                        final String subpath = entry.getName().substring("overrides/".length());
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

    private Path downloadModFile(SharedFetch preparedFetch, UriBuilder uriBuilder, Path modsDir, int projectID, int fileID) {
        try {
            final CurseForgeFile file = getModFileInfo(preparedFetch, uriBuilder, projectID, fileID);
            if (!isServerMod(file)) {
                log.debug("Skipping {} since it is a client mod", file.getFileName());
                return null;
            }

            log.info("Download/confirm mod {} @ {}:{}",
                // several mods have non-descriptive display names, like "v1.0.0", so filename tends to be better
                file.getFileName(),
                projectID, fileID
            );

            return preparedFetch.fetch(
                    normalizeDownloadUrl(file.getDownloadUrl())
                )
                .toDirectory(modsDir)
                .skipExisting(true)
                .execute();
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

    private static CurseForgeFile getModFileInfo(SharedFetch preparedFetch, UriBuilder uriBuilder, int projectID, int fileID)
        throws IOException {
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
}
