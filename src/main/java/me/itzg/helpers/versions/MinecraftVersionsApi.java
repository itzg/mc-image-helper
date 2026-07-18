package me.itzg.helpers.versions;

import java.net.URI;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.ChecksumAlgo;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.versions.AssetsManifest.JarInfo;
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
     * @return information about the resolved version or empty if not valid/present
     */
    public Mono<MinecraftVersionInfo> resolve(String inputVersion) {
        return sharedFetch.fetch(
            manifestUrl
        )
            .toObject(VersionManifestV2.class)
            .assemble()
            .flatMap(manifest -> {
                final String actualVersion;
                if (inputVersion == null
                    || inputVersion.equalsIgnoreCase("latest")
                    || inputVersion.equalsIgnoreCase("release")) {
                    actualVersion = manifest.getLatest().getRelease();
                }
                else if (inputVersion.equalsIgnoreCase("snapshot")) {
                    actualVersion = manifest.getLatest().getSnapshot();
                }
                else {
                    actualVersion = inputVersion;
                }

                return Mono.justOrEmpty(
                    manifest.getVersions().stream()
                        .filter(v -> v.getId().equalsIgnoreCase(actualVersion))
                        .map(Version::toVersionInfo)
                        .findFirst()
                );
            })
            .doOnNext(resolved -> log.debug("Resolved given Minecraft version {} to {}", inputVersion, resolved.getVersion()))
            .switchIfEmpty(Mono.error(() -> new InvalidParameterException(String.format("Minecraft version '%s' is not valid", inputVersion))));
    }

    /**
     * @param version the version of Minecraft as returned from {@link MinecraftVersionsApi#resolve(String)}
     * @return information about the server jar or empty if the version has no server
     */
    public Mono<MinecraftJarInfo> getServerJar(MinecraftVersionInfo version) {
        return sharedFetch.fetch(
                version.getManifestUrl()
            )
            .toObject(AssetsManifest.class)
            .assemble()
            .flatMap(m -> {
                final JarInfo server = m.getDownloads().getServer();
                if (server != null) {
                    return Mono.just(new MinecraftJarInfo(server.getUrl(), server.getSize(), ChecksumAlgo.SHA1, server.getSha1()));
                }
                return Mono.empty();
            });
    }
}
