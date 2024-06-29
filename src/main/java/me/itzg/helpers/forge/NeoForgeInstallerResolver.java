package me.itzg.helpers.forge;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.mvn.MavenMetadata;
import me.itzg.helpers.mvn.MavenRepoApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class NeoForgeInstallerResolver implements InstallerResolver {

    public static final String GROUP_ID = "net.neoforged";
    public static final String ARTIFACT_ID_FORGE_LIKE = "forge";
    public static final String ARTIFACT_ID = "neoforge";
    public static final String FORGE_LIKE_VERSION = "1.20.1";

    private final MavenRepoApi mavenRepoApi;
    private final String requestedMinecraftVersion;
    private final String requestedNeoForgeVersion;

    public NeoForgeInstallerResolver(SharedFetch sharedFetch,
        @NotNull
        String requestedMinecraftVersion,
        @Nullable
        String requestedNeoForgeVersion
    ) {
        this(sharedFetch, requestedMinecraftVersion, requestedNeoForgeVersion, "https://maven.neoforged.net/releases");
    }

    NeoForgeInstallerResolver(SharedFetch sharedFetch,
        @NotNull
        String requestedMinecraftVersion,
        @Nullable
        String requestedNeoForgeVersion, String neoforgeMavenRepoUrl
    ) {
        mavenRepoApi = new MavenRepoApi(neoforgeMavenRepoUrl, sharedFetch);

        this.requestedMinecraftVersion = requireNonNull(requestedMinecraftVersion);
        this.requestedNeoForgeVersion = requestedNeoForgeVersion;

        log.debug("Requesting NeoForge version={} for minecraft={}", requestedNeoForgeVersion, requestedMinecraftVersion);
    }

    enum NeoForgeVersionType {
        LATEST,
        BETA,
        SPECIFIC
    }

    @Override
    public VersionPair resolve() {
        if (useForgeArtifactId(requestedMinecraftVersion)) {
            return resolveForgeLike();
        }

        final String[] minecraftVersion = requestedMinecraftVersion.equalsIgnoreCase("latest") ?
            null : splitMinecraftVersion();

        final NeoForgeVersionType neoForgeVersionType;
        final String[] neoforgeVersion;
        if ("beta".equalsIgnoreCase(requestedNeoForgeVersion)) {
            neoForgeVersionType = NeoForgeVersionType.BETA;
            neoforgeVersion = null;
        }
        else if (requestedNeoForgeVersion == null || requestedNeoForgeVersion.equalsIgnoreCase("latest")) {
            neoForgeVersionType = NeoForgeVersionType.LATEST;
            neoforgeVersion = null;
        }
        else {
            neoForgeVersionType = NeoForgeVersionType.SPECIFIC;
            neoforgeVersion = splitNeoforgeVersion(requestedNeoForgeVersion);
            if (neoforgeVersion.length < 3) {
                throw new InvalidParameterException("Malformed NeoForge version: " + requestedNeoForgeVersion);
            }
        }

        final MavenMetadata metadata = mavenRepoApi.fetchMetadata(GROUP_ID, ARTIFACT_ID)
            .block();

        if (metadata == null) {
            throw new GenericException("Unable to resolve NeoForge metadata");
        }

        final String result = metadata.getVersioning().getVersion().stream()
            .filter(s -> {
                final String[] parts = splitNeoforgeVersion(s);
                if (parts.length < 3) {
                    log.debug("Skipping malformed version in metadata: {}", s);
                    return false;
                }

                if (neoForgeVersionType == NeoForgeVersionType.SPECIFIC) {
                    // NOTE: ignores beta qualifier
                    return IntStream.range(0, 3)
                        .allMatch(i -> parts[i].equals(neoforgeVersion[i]));
                }
                else {
                    if (minecraftVersion != null) {
                        // minor.patch of minecraft version != major.minor of neoforge version
                        final String minor = minecraftVersion[1];
                        final String patch = minecraftVersion.length > 2 ? minecraftVersion[2] : "0";

                        if (!(minor.equals(parts[0]) && patch.equals(parts[1]))) {
                            return false;
                        }
                    }

                    if (parts.length >= 4 && parts[3].equals("beta")) {
                        return neoForgeVersionType == NeoForgeVersionType.BETA;
                    }

                    return true;
                }
            })
            .reduce((s, s2) -> s2)
            .orElse(null);

        return result != null ? new VersionPair(deriveMinecraftVersion(result), result) : null;
    }

    private boolean useForgeArtifactId(String minecraftVersion) {
        return FORGE_LIKE_VERSION.equals(minecraftVersion);
    }

    @NotNull
    private static String deriveMinecraftVersion(String result) {
        final String[] resolvedParts = splitNeoforgeVersion(result);

        // Minecraft versions never end with ".0"...they just leave it off
        return resolvedParts[1].equals("0") ? String.join(".", "1", resolvedParts[0])
                : String.join(".", "1", resolvedParts[0], resolvedParts[1]
        );
    }

    @NotNull
    private static String[] splitNeoforgeVersion(String s) {
        return s.split("[.-]", 4);
    }

    @NotNull
    private String[] splitMinecraftVersion() {
        final String[] parts = requestedMinecraftVersion.split("\\.", 3);
        if (parts.length < 2) {
            throw new GenericException("Expected at least two parts to Minecraft version: " + requestedMinecraftVersion);
        }
        return parts;
    }

    private VersionPair resolveForgeLike() {
        final MavenMetadata forgeMetadata = mavenRepoApi.fetchMetadata(GROUP_ID, ARTIFACT_ID_FORGE_LIKE)
            .block();

        if (forgeMetadata == null) {
            throw new GenericException("Unable to resolve Forge metadata");
        }

        final boolean useLatestMinecraft = requestedMinecraftVersion == null
            || requestedMinecraftVersion.equalsIgnoreCase("latest");
        final boolean useLatestNeoForge = requestedNeoForgeVersion == null
            || requestedNeoForgeVersion.equalsIgnoreCase("latest");

        final List<VersionPair> results = forgeMetadata.getVersioning().getVersion().stream()
            .filter(version -> version.indexOf('-') > 0)
            .map(version -> {
                final int dashPos = version.indexOf('-');
                return new VersionPair(version.substring(0, dashPos), version.substring(dashPos + 1));
            })
            .filter(versionPair -> useLatestMinecraft || requestedMinecraftVersion.equals(versionPair.minecraft))
            .filter(versionPair -> useLatestNeoForge || requestedNeoForgeVersion.equals(versionPair.forge))
            .collect(Collectors.toList());

        if (results.isEmpty()) {
            throw new InvalidParameterException(
                String.format("Unable to locate requested NeoForge version '%s' for Minecraft %s",
                    requestedNeoForgeVersion, requestedMinecraftVersion
                ));
        }

        return results.get(results.size() - 1);
    }

    /**
     * @param minecraftVersion is ignored for NeoForge
     * @param forgeVersion the neoforge version with possible "-beta" qualifier
     */
    @Override
    public Path download(String minecraftVersion, String forgeVersion, Path outputDir) {
        if (useForgeArtifactId(minecraftVersion)) {
            return mavenRepoApi.download(outputDir, GROUP_ID, ARTIFACT_ID_FORGE_LIKE,
                    minecraftVersion + "-" + forgeVersion,
                    "jar", "installer"
                )
                .block();
        }
        else {
            return mavenRepoApi.download(outputDir, GROUP_ID, ARTIFACT_ID,
                    forgeVersion,
                    "jar", "installer"
                )
                .block();
        }
    }

    @Override
    public void cleanup(Path forgeInstallerJar) {
        try {
            Files.delete(forgeInstallerJar);
        } catch (IOException e) {
            log.warn("Failed to delete NeoForge installer", e);
        }
    }
}
