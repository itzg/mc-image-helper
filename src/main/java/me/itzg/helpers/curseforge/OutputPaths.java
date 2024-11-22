package me.itzg.helpers.curseforge;

import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Getter
class OutputPaths {
    private final Mono<Path> modsDir;
    private final Mono<Path> pluginsDir;
    private final Mono<Path> worldsDir;
}
