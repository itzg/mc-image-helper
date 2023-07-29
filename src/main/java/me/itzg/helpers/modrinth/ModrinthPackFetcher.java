package me.itzg.helpers.modrinth;

import java.nio.file.Path;

import reactor.core.publisher.Mono;

public interface ModrinthPackFetcher {
    Mono<Path> fetchModpack(ModrinthModpackManifest prevManifest);
}
