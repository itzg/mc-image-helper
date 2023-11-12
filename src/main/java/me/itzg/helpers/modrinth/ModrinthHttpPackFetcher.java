package me.itzg.helpers.modrinth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class ModrinthHttpPackFetcher implements ModrinthPackFetcher {
    private final ModrinthApiClient apiClient;
    private final Path destFilePath;
    private final URI modpackUri;

    ModrinthHttpPackFetcher(ModrinthApiClient apiClient, Path basePath, URI uri) {
        this.apiClient = apiClient;
        this.destFilePath = basePath.resolve("modpack.mrpack");
        this.modpackUri = uri;
    }

    @Override
    public Mono<FetchedPack> fetchModpack(ModrinthModpackManifest prevManifest) {
        return apiClient.downloadFileFromUrl(
                destFilePath, modpackUri,
                (uri, file, contentSizeBytes) ->
                    log.info("Downloaded {}", destFilePath)
            )
            .map(mrPackFile -> new FetchedPack(mrPackFile, "custom", deriveVersionId(), deriveVersionName()));
    }

    private String deriveVersionName() {
        final int lastSlash = modpackUri.getPath().lastIndexOf('/');
        return lastSlash > 0 ? modpackUri.getPath().substring(lastSlash + 1)
            : "unknown";
    }

    private String deriveVersionId() {
        return Base64.getUrlEncoder().encodeToString(modpackUri.toString().getBytes(StandardCharsets.UTF_8));
    }
}
