package me.itzg.helpers.versions;

import java.net.URI;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.versions.VersionManifestV2.Version;
import reactor.core.publisher.Mono;

@Slf4j
public class MinecraftVersionsApi {

    @Setter
    private URI manifestUrl = URI.create("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");

    private final SharedFetch sharedFetch;

    public MinecraftVersionsApi(SharedFetch sharedFetch) {
        this.sharedFetch = sharedFetch;
    }

    /**
     * @param inputVersion latest, release, snapshot or a specific version
     * @return the resolved version or empty if not valid/present
     */
    public Mono<String> resolve(String inputVersion) {
        return sharedFetch.fetch(
            manifestUrl
        )
            .toObject(VersionManifestV2.class)
            .assemble()
            .flatMap(manifest -> {
                if (inputVersion == null
                    || inputVersion.equalsIgnoreCase("latest")
                    || inputVersion.equalsIgnoreCase("release")) {
                    return Mono.just(manifest.getLatest().getRelease());
                }
                else if (inputVersion.equalsIgnoreCase("snapshot")) {
                    return Mono.just(manifest.getLatest().getSnapshot());
                }
                else {
                    return Mono.justOrEmpty(
                        manifest.getVersions().stream()
                            .map(Version::getId)
                            .filter(id -> id.equalsIgnoreCase(inputVersion))
                            .findFirst()
                    );
                }
            })
            .doOnNext(resolvedVersion -> log.debug("Resolved given Minecraft version {} to {}", inputVersion, resolvedVersion))
            .switchIfEmpty(Mono.error(() -> new InvalidParameterException(String.format("Minecraft version '%s' is not valid", inputVersion))));
    }
}
