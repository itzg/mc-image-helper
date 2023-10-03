package me.itzg.helpers.forge;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
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
    public static final String ARTIFACT_ID = "forge";

    private final MavenRepoApi mavenRepoApi;
    private final String requestedMinecraftVersion;
    private final String requestedNeoForgeVersion;

    public NeoForgeInstallerResolver(SharedFetch sharedFetch,
        @NotNull
        String requestedMinecraftVersion,
        @Nullable
        String requestedNeoForgeVersion
    ) {
        mavenRepoApi = new MavenRepoApi("https://maven.neoforged.net/releases", sharedFetch);

        this.requestedMinecraftVersion = requireNonNull(requestedMinecraftVersion);
        this.requestedNeoForgeVersion = requestedNeoForgeVersion;

        log.debug("Requesting NeoForge version={} for minecraft={}", requestedNeoForgeVersion, requestedMinecraftVersion);
    }

    @Override
    public VersionPair resolve() {
        final MavenMetadata forgeMetadata = mavenRepoApi.fetchMetadata(GROUP_ID, ARTIFACT_ID)
            .block();

        if (forgeMetadata == null) {
            throw new GenericException("Unable to resolve Forge metadata");
        }

        final boolean useLatestMinecraft = requestedMinecraftVersion == null
            || requestedMinecraftVersion.equalsIgnoreCase("latest");
        final boolean useLatestNeoForge = requestedNeoForgeVersion == null
            || requestedNeoForgeVersion.equalsIgnoreCase("latest");

        final List<VersionPair> results = forgeMetadata.getVersioning().getVersion().stream()
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

    @Override
    public Path download(String minecraftVersion, String forgeVersion, Path outputDir) {
        return mavenRepoApi.download(outputDir, GROUP_ID, ARTIFACT_ID,
                minecraftVersion + "-" + forgeVersion,
                "jar", "installer"
            )
            .block();
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
