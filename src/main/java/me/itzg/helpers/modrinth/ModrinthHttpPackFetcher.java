package me.itzg.helpers.modrinth;

import java.net.URI;
import java.nio.file.Path;

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
    public Mono<Path> fetchModpack(ModrinthModpackManifest prevManifest) {
        return this.apiClient.downloadFileFromUrl(
            this.destFilePath, this.modpackUri,
            (uri, file, contentSizeBytes) ->
                log.info("Downloaded {}", this.destFilePath));
    }
}
