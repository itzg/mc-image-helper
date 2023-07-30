package me.itzg.helpers.curseforge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.model.Category;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.curseforge.model.CurseForgeResponse;
import me.itzg.helpers.curseforge.model.GetCategoriesResponse;
import me.itzg.helpers.curseforge.model.GetModFileResponse;
import me.itzg.helpers.curseforge.model.GetModFilesResponse;
import me.itzg.helpers.curseforge.model.GetModResponse;
import me.itzg.helpers.curseforge.model.ModsSearchResponse;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadStatusHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.json.ObjectMappers;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class CurseForgeApiClient implements AutoCloseable {
    private static final String API_KEY_HEADER = "x-api-key";

    private final SharedFetch preparedFetch;
    private final UriBuilder uriBuilder;
    private final String gameId;

    public CurseForgeApiClient(String apiBaseUrl, String apiKey, SharedFetch.Options sharedFetchOptions, String gameId
    ) {
        this.preparedFetch = Fetch.sharedFetch("install-curseforge",
            (sharedFetchOptions != null ? sharedFetchOptions : SharedFetch.Options.builder().build())
                .withHeader(API_KEY_HEADER, apiKey)
        );
        this.uriBuilder = UriBuilder.withBaseUrl(apiBaseUrl);
        this.gameId = gameId;
    }

    @Override
    public void close() {
        preparedFetch.close();
    }

    CategoryInfo loadModpacksCategoryInfo(Set<String> applicableClassIdSlugs) {
        return preparedFetch
            // get only categories that are classes, like mc-mods
            .fetch(uriBuilder.resolve("/categories?gameId={gameId}&classesOnly=true", gameId))
            .toObject(GetCategoriesResponse.class)
            .assemble()
            .flatMap(resp -> {
                    final Map<Integer, Category> contentClassIds = new HashMap<>();
                    Integer modpackClassId = null;

                    for (final Category category : resp.getData()) {
                        if (applicableClassIdSlugs.contains(category.getSlug())) {
                            contentClassIds.put(category.getId(), category);
                        }
                        if (category.getSlug().equals(CurseForgeInstaller.CATEGORY_SLUG_MODPACKS)) {
                            modpackClassId = category.getId();
                        }
                    }

                    if (modpackClassId == null) {
                        return Mono.error(new GenericException("Unable to lookup classId for modpacks"));
                    }

                    return Mono.just(new CategoryInfo(contentClassIds, modpackClassId));
                }
            )
            .block();
    }

    CurseForgeMod searchMod(String slug, CategoryInfo categoryInfo) {
        final ModsSearchResponse searchResponse = preparedFetch.fetch(
                uriBuilder.resolve("/mods/search?gameId={gameId}&slug={slug}&classId={classId}",
                    gameId, slug, categoryInfo.modpackClassId
                )
            )
            .toObject(ModsSearchResponse.class)
            .execute();

        if (searchResponse.getData() == null || searchResponse.getData().isEmpty()) {
            throw new GenericException("No mods found with slug={}" + slug);
        } else if (searchResponse.getData().size() > 1) {
            throw new GenericException("More than one mod found with slug=" + slug);
        } else {
            return searchResponse.getData().get(0);
        }

    }

    /**
     * @param fileMatcher a pattern to match desired modpack file name or null to obtain first/newest
     */
    public CurseForgeFile resolveModpackFile(
        CurseForgeMod mod,
        String fileMatcher
    ) {
        // NOTE latestFiles in mod is only one or two files, so retrieve the full list instead
        final GetModFilesResponse resp = preparedFetch.fetch(
                uriBuilder.resolve("/mods/{modId}/files", mod.getId()
                )
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

    Mono<Integer> slugToId(CategoryInfo categoryInfo,
        String slug
    ) {
        return preparedFetch
            .fetch(
                uriBuilder.resolve("/mods/search?gameId={gameId}&slug={slug}", gameId, slug)
            )
            .toObject(ModsSearchResponse.class)
            .assemble()
            .map(resp ->
                resp.getData().stream()
                    .filter(curseForgeMod -> categoryInfo.contentClassIds.containsKey(curseForgeMod.getClassId()))
                    .findFirst()
                    .map(CurseForgeMod::getId)
                    .orElseThrow(() -> new GenericException("Unable to resolve slug into ID (no matches): " + slug))
            );
    }

    public Mono<CurseForgeMod> getModInfo(
        int projectID
    ) {
        log.debug("Getting mod metadata for {}", projectID);

        return preparedFetch.fetch(
                uriBuilder.resolve("/mods/{modId}", projectID)
            )
            .toObject(GetModResponse.class)
            .assemble()
            .checkpoint("Getting mod info for " + projectID)
            .map(GetModResponse::getData);
    }

    public Mono<CurseForgeFile> getModFileInfo(
        int projectID, int fileID
    ) {
        log.debug("Getting mod file metadata for {}:{}", projectID, fileID);

        return preparedFetch.fetch(
                uriBuilder.resolve("/mods/{modId}/files/{fileId}", projectID, fileID)
            )
            .toObject(GetModFileResponse.class)
            .assemble()
            .onErrorMap(FailedRequestException.class::isInstance, e -> {
                final FailedRequestException fre = (FailedRequestException) e;
                if (fre.getStatusCode() == 400) {
                    if (isNotFoundResponse(fre.getBody())) {
                        return new InvalidParameterException("Requested file not found for modpack", e);
                    }
                }
                return e;
            })
            .map(GetModFileResponse::getData);
    }

    public Mono<Path> download(CurseForgeFile cfFile, Path outputFile, FileDownloadStatusHandler handler) {
        return preparedFetch.fetch(
                normalizeDownloadUrl(cfFile.getDownloadUrl())
            )
            .toFile(outputFile)
            .skipExisting(true)
            .handleStatus(handler)
            .assemble();
    }

    public Mono<Path> downloadTemp(CurseForgeFile cfFile, String suffix, FileDownloadStatusHandler handler) {
        return Mono.just(cfFile)
            .publishOn(Schedulers.boundedElastic())
            .flatMap(curseForgeFile -> {
                    final Path outFile;
                    try {
                        //noinspection BlockingMethodInNonBlockingContext
                        outFile = Files.createTempFile("curseforge-modpack", suffix);
                    } catch (IOException e) {
                        return Mono.error(e);
                    }

                    return preparedFetch.fetch(normalizeDownloadUrl(cfFile.getDownloadUrl()))
                        .toFile(outFile)
                        .handleStatus(handler)
                        .assemble();
                }
            );
    }

    private static boolean isNotFoundResponse(String body) {
        try {
            final CurseForgeResponse<Void> resp = ObjectMappers.defaultMapper().readValue(
                body, new TypeReference<CurseForgeResponse<Void>>() {
                }
            );
            return resp.getError().startsWith("Error: 404");
        } catch (JsonProcessingException e) {
            throw new GenericException("Unable to parse error response", e);
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
