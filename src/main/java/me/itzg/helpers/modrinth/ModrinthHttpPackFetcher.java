package me.itzg.helpers.modrinth;

import java.net.URI;
import java.nio.file.Path;

import me.itzg.helpers.http.SharedFetch;
import reactor.core.publisher.Mono;

public class ModrinthHttpPackFetcher implements ModrinthPackFetcher {
    private final SharedFetch sharedFetch;
    private final Path destFilePath;
    private final URI modpackUri;

    ModrinthHttpPackFetcher(SharedFetch sharedFetch, Path basePath, URI uri) {
        this.sharedFetch = sharedFetch;
        this.destFilePath = basePath.resolve("modpack.mrpack");
        this.modpackUri = uri;
    }

    @Override
    public Mono<Path> fetchModpack(ModrinthModpackManifest prevManifest) {
        return sharedFetch.fetch(modpackUri).toFile(destFilePath).assemble();
    }
}
