package me.itzg.helpers.forge;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.mvn.MavenMetadata;
import me.itzg.helpers.mvn.MavenRepoApi;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

@Slf4j
public class NeoForgeInstallerResolver implements InstallerResolver {

    public static final String GROUP_ID = "net.neoforged";
    public static final String ARTIFACT_ID_FORGE_LIKE = "forge";
    public static final String ARTIFACT_ID = "neoforge";
    public static final String FORGE_LIKE_VERSION = "1.20.1";
    public static final String DEFAULT_MVN_URL = "https://maven.neoforged.net/releases";

    private final MavenRepoApi mavenRepoApi;
    private final String requestedMinecraftVersion;
    private final String requestedNeoForgeVersion;

    public NeoForgeInstallerResolver(SharedFetch sharedFetch,
        @NotNull
        String requestedMinecraftVersion,
        @Nullable
        String requestedNeoForgeVersion
    ) {
        this(sharedFetch, requestedMinecraftVersion, requestedNeoForgeVersion, DEFAULT_MVN_URL);
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
    public VersionPair resolve(ForgeManifest prevManifest) {
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
            neoforgeVersion = splitNeoforgeVersion(requestedNeoForgeVersion).getVersion();
            if (neoforgeVersion.length < 3) {
                throw new InvalidParameterException("Malformed NeoForge version: " + requestedNeoForgeVersion);
            }

            if (prevManifest != null) {
                if (prevManifest.getMinecraftVersion().equals(requestedMinecraftVersion)
                    && prevManifest.getForgeVersion().equals(requestedNeoForgeVersion)) {
                    return new VersionPair(requestedMinecraftVersion, requestedNeoForgeVersion);
                }

            }
        }

        final MavenMetadata metadata = mavenRepoApi.fetchMetadata(GROUP_ID, ARTIFACT_ID)
            .block();

        if (metadata == null) {
            throw new GenericException("Unable to resolve NeoForge metadata");
        }

        final String result = metadata.getVersioning().getVersion().stream()
            .filter(s -> {
                final @NotNull ParsedVersion parsed = splitNeoforgeVersion(s);
                if (parsed.version.length < 3) {
                    log.debug("Skipping malformed version in metadata: {}", s);
                    return false;
                }
                // consider only released and beta for now
                if (!parsed.released && !parsed.beta) {
                    log.debug("Skipping non-release/beta version in metadata: {}", s);
                    return false;
                }

                if (neoForgeVersionType == NeoForgeVersionType.SPECIFIC) {
                    // NOTE: ignores beta qualifier
                    return Arrays.equals(parsed.version, neoforgeVersion);
                }
                else {
                    if (notMatchingSpecific(minecraftVersion, parsed)) {
                        return false;
                    }

                    // Match up request for beta or not
                    return (neoForgeVersionType == NeoForgeVersionType.BETA) == parsed.beta;
                }
            })
            // pick the highest version from a or b
            .reduce((a, b) ->
                new ComparableVersion(a).compareTo(new ComparableVersion(b)) > 0 ? a : b
            )
            .orElse(null);

        return result != null ? new VersionPair(deriveMinecraftVersion(result), result) : null;
    }

    private static boolean notMatchingSpecific(String[] minecraftVersion, @NonNull ParsedVersion parsed) {
        if (minecraftVersion != null) {
            if (parsed.isYearBased()) {
                // if minecraft version is 2-part, then it has dropped trailing ".0"
                if (minecraftVersion.length == 2) {
                    return !(minecraftVersion[0].equals(parsed.version[0])
                        && minecraftVersion[1].equals(parsed.version[1])
                        && parsed.version[2].equals("0")
                    );
                }
                // else compare all three slots
                else {
                    return !(minecraftVersion[0].equals(parsed.version[0])
                        && minecraftVersion[1].equals(parsed.version[1])
                        && minecraftVersion[2].equals(parsed.version[2])
                    );
                }
            }
            // not year-based where minecraft version has a leading "1."
            else {
                // if minecraft version is 2-part, then it has dropped trailing ".0"
                if (minecraftVersion.length == 2) {
                    return !(minecraftVersion[1].equals(parsed.version[0])
                        && parsed.version[1].equals("0")
                    );
                }
                // else compare the minor and patch of minecraft version with first two of parsed
                else {
                    return !(minecraftVersion[1].equals(parsed.version[0])
                        && minecraftVersion[2].equals(parsed.version[1])
                    );
                }
            }
        }
        return false;
    }

    private boolean useForgeArtifactId(String minecraftVersion) {
        return FORGE_LIKE_VERSION.equals(minecraftVersion);
    }

    @NotNull
    private static String deriveMinecraftVersion(String result) {
        final @NotNull ParsedVersion resolved = splitNeoforgeVersion(result);
        if (resolved.isYearBased()) {
            // Minecraft versions never end with ".0"...they just leave it off
            return resolved.version[2].equals("0") ?
                String.join(".", resolved.version[0], resolved.version[1]) :
                String.join(".", resolved.version[0], resolved.version[1], resolved.version[2]);
        }
        else {
            // Minecraft versions never end with ".0"...they just leave it off
            return resolved.version[1].equals("0") ? String.join(".", "1", resolved.version[0])
                : String.join(".", "1", resolved.version[0], resolved.version[1]
                );
        }
    }

    @Data
    static class ParsedVersion {
        final String[] version;
        final boolean released;
        // 26.1.0.15-beta
        // and not 26.1.0.0-alpha.15+pre-3
        final boolean beta;

        public boolean isYearBased() {
            // Since NeoForge always includes ".0" for patch it is safe to conditionalize on slot count
            // such as 26.1.1.0
            return version.length == 4;
        }
    }

    @NotNull
    private static ParsedVersion splitNeoforgeVersion(String s) {
        // pre 26.x has three numerical spots and optional "beta"
        // post 26.x has four numerical spots and optional "beta"
        final String[] versionAndBeta = s.split("-", 2);
        final String[] version = versionAndBeta[0].split("\\.", 4);

        return new ParsedVersion(version,
            versionAndBeta.length == 1,
            versionAndBeta.length > 1 && versionAndBeta[1].equalsIgnoreCase("beta")
        );
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

    @Override
    public String getDescription() {
        return String.format("Minecraft %s NeoForge %s", requestedMinecraftVersion, requestedNeoForgeVersion);
    }
}
