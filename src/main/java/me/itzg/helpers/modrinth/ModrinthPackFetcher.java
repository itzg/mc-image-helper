package me.itzg.helpers.modrinth;

import reactor.core.publisher.Mono;

public interface ModrinthPackFetcher {

    /**
     * @return the fetched modpack or empty if the requested modpack was already up-to-date
     */
    Mono<FetchedPack> fetchModpack(ModrinthModpackManifest prevManifest);
}
