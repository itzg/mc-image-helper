package me.itzg.helpers.modrinth.pack;

import java.nio.file.Path;

import me.itzg.helpers.modrinth.ModrinthModpackManifest;
import reactor.core.publisher.Mono;

public interface IModrinthPackFetcher {
    Mono<Path> fetchModpack(ModrinthModpackManifest prevManifest);
}
