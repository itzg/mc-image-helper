package me.itzg.helpers.forge;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.forge.model.PromotionsSlim;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.Uris;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class ForgeInstallerResolver implements InstallerResolver {
    public static final String LATEST = "latest";
    public static final String RECOMMENDED = "recommended";
    public static final String DEFAULT_PROMOTIONS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    public static final String DEFAULT_MAVEN_REPO_URL = "https://maven.minecraftforge.net";

    private final String promotionsUrl;
    private final String mavenRepoUrl;
    private final SharedFetch sharedFetch;
    private final String requestedMinecraftVersion;
    private final String requestedForgeVersion;

    public ForgeInstallerResolver(SharedFetch sharedFetch,
        String requestedMinecraftVersion, String requestedForgeVersion,
        String promotionsUrl, String mavenRepoUrl
    ) {
        this.sharedFetch = sharedFetch;
        this.requestedMinecraftVersion = requestedMinecraftVersion;
        this.requestedForgeVersion = requestedForgeVersion;
        this.promotionsUrl = promotionsUrl;
        this.mavenRepoUrl = mavenRepoUrl;
    }

    @Override
    public VersionPair resolve(ForgeManifest prevManifest) {
        if (prevManifest != null) {
            final String prevMinecraftVersion = prevManifest.getMinecraftVersion();
            final String prevForgeVersion = prevManifest.getForgeVersion();
            if (prevMinecraftVersion.equals(requestedMinecraftVersion)
                && prevForgeVersion.equals(requestedForgeVersion)) {
                log.debug("Resolved Minecraft {} Forge {} from previous manifest", prevMinecraftVersion, prevForgeVersion);
                return new VersionPair(requestedMinecraftVersion, requestedForgeVersion);
            }
        }

        final PromotionsSlim promotionsSlim = loadPromotions();
        if (promotionsSlim.getPromos().isEmpty()) {
            throw new GenericException("No versions were available in Forge promotions");
        }

        final String resolvedMinecraftVersion;
        final String resolvedForgeVersion;
            resolvedMinecraftVersion = resolveMinecraftVersion(requestedMinecraftVersion, promotionsSlim);
            try {
                resolvedForgeVersion = resolveForgeVersion(resolvedMinecraftVersion, requestedForgeVersion, promotionsSlim);
            } catch (IOException e) {
                throw new GenericException("Failed to resolve forge version", e);
            }

        return new VersionPair(resolvedMinecraftVersion, resolvedForgeVersion);
    }

    @Override
    public Path download(String minecraftVersion, String forgeVersion, Path outputDir) {
        log.info("Downloading Forge installer {} for Minecraft {}", forgeVersion, minecraftVersion);

        final Path installerJar = outputDir.resolve(String.format("forge-installer-%s-%s",
            minecraftVersion, forgeVersion
        ) +".jar");

        final Path result = Flux.just(
                // every few major versions Forge changes their version qualifier scheme
                String.join("-", minecraftVersion, forgeVersion),
                String.join("-", minecraftVersion, forgeVersion, minecraftVersion),
                String.join("-", minecraftVersion, forgeVersion, "mc172")
            )
            // concatMap ensures each is tried (to be found) before the next
            .concatMap(versionToTry ->
                    sharedFetch.fetch(Uris.populateToUri(
                            mavenRepoUrl
                                + "/net/minecraftforge/forge/{version}/forge-{version}-installer.jar",
                            versionToTry, versionToTry
                        ))
                        .toFile(installerJar)
                        .skipExisting(true)
                        .acceptContentTypes(Collections.singletonList("application/java-archive"))
                        .assemble()
                        .checkpoint("downloading installer jar for " + versionToTry)
                        // just skip this one if not found
                        .onErrorResume(FailedRequestException::isNotFound, throwable -> Mono.empty())
                )
            .blockFirst();

        if (result == null) {
            throw new GenericException(String.format(
                "Failed to locate forge installer for Minecraft %s Forge %s", minecraftVersion, forgeVersion
            ));
        }
        else {
            return result;
        }
    }

    @Override
    public void cleanup(Path forgeInstallerJar) {
        try {
            Files.delete(forgeInstallerJar);
        } catch (IOException e) {
            log.warn("Failed to delete installer jar {}", forgeInstallerJar);
        }
    }

    @Override
    public String getDescription() {
        return String.format("Minecraft %s Forge %s", requestedMinecraftVersion, requestedForgeVersion);
    }

    private PromotionsSlim loadPromotions() {
        return sharedFetch.fetch(URI.create(promotionsUrl))
            .userAgentCommand("forge")
            .toObject(PromotionsSlim.class)
            .execute();
    }

    private String resolveMinecraftVersion(String minecraftVersion, PromotionsSlim promotionsSlim) {
        if (minecraftVersion == null || minecraftVersion.equalsIgnoreCase(LATEST)) {
            return promotionsSlim.getPromos().entrySet().stream()
                .map(ForgeInstallerResolver::parsePromoEntry)
                // pick off the last entry, where order is significant since JSON parsing retains ordering
                .reduce((lhs, rhs) -> rhs)
                .map(promoEntry -> promoEntry.mcVersion)
                .orElseThrow(() -> new GenericException("No versions were available in Forge promotions"));
        } else {
            return minecraftVersion;
        }
    }

    private static PromoEntry parsePromoEntry(Entry<String, String> entry) {
        // each entry is like
        // "1.19-recommended": "41.1.0"
        final String[] keyParts = entry.getKey().split("-", 2);
        return new PromoEntry(keyParts[0], keyParts[1].toLowerCase(), entry.getValue());
    }

    private String resolveForgeVersion(String minecraftVersion, String forgeVersion, PromotionsSlim promotionsSlim) throws IOException {
        final String normalized = forgeVersion.toLowerCase();
        if (!normalized.equals(LATEST) && !normalized.equals(RECOMMENDED)) {
            return forgeVersion;
        }

        final Map<String, String> options = promotionsSlim.getPromos().entrySet().stream()
            .map(ForgeInstallerResolver::parsePromoEntry)
            // narrow to just applicable minecraft version
            .filter(entry -> entry.getMcVersion().equals(minecraftVersion))
            // ...and arrive at a map that has one or two entries for latest and/or recommended
            .collect(Collectors.toMap(
                PromoEntry::getPromo,
                PromoEntry::getForgeVersion
            ));

        log.debug("Narrowed forge versions to {} and looking for {}", options, normalized);

        if (!options.isEmpty()) {
            final String result = options.get(normalized);
            if (result != null) {
                return result;
            } else {
                // ...otherwise need to fall back to what we have
                return options.values().iterator().next();
            }
        }
        else {
            throw new InvalidParameterException(String.format("Minecraft version %s not available from Forge", minecraftVersion));
        }
    }

}
