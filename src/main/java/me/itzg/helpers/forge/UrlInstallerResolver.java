package me.itzg.helpers.forge;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;

@Slf4j
public class UrlInstallerResolver implements InstallerResolver {

    private static final List<String> INSTALLER_CONTENT_TYPES = List.of(
        "application/java-archive",
        "application/octet-stream"
    );

    private final SharedFetch sharedFetch;
    private final URI installerUri;
    private final Path outputDir;
    private final boolean forceReinstall;
    private Path fetchedJar;

    public UrlInstallerResolver(SharedFetch sharedFetch, URI installerUri, Path outputDir, boolean forceReinstall) {
        this.sharedFetch = sharedFetch;
        this.installerUri = installerUri;
        this.outputDir = outputDir;
        this.forceReinstall = forceReinstall;
    }

    @Override
    public VersionPair resolve(ForgeManifest prevManifest) {
        if (!forceReinstall && sameRecordedInstaller(prevManifest)) {
            log.debug("Resolved Forge installer from previous manifest for {}", installerUri);
            return new VersionPair(prevManifest.getMinecraftVersion(), prevManifest.getForgeVersion());
        }

        // The resulting download is identified and used in download(), so fetchInstaller is not called twice.
        final Path installerJar = fetchInstaller(outputDir);
        try {
            final VersionPair versions = InstallerVersionExtractor.extract(installerJar);
            if (versions == null) {
                throw new GenericException("Failed to locate version from forge installer " + installerUri);
            }
            return versions;
        } catch (IOException e) {
            cleanup(installerJar);
            throw new GenericException("Failed to extract version from forge installer " + installerUri, e);
        } catch (RuntimeException e) {
            cleanup(installerJar);
            throw e;
        }
    }

    @Override
    public Path download(String minecraftVersion, String forgeVersion, Path outputDir) {
        if (fetchedJar != null && Files.exists(fetchedJar)) {
            return fetchedJar;
        }
        return fetchInstaller(outputDir);
    }

    @Override
    public void cleanup(Path forgeInstallerJar) {
        fetchedJar = null;
        deleteIfExists(forgeInstallerJar);
        deleteIfExists(forgeInstallerJar.resolveSibling(forgeInstallerJar.getFileName() + ".download"));
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}", path);
        }
    }

    @Override
    public String getDescription() {
        return String.format("Forge installer from %s", installerUri);
    }

    @Override
    public String getInstallerUrl() {
        return installerUri.toString();
    }

    private boolean sameRecordedInstaller(ForgeManifest prevManifest) {
        return prevManifest != null
            && installerUri.toString().equals(prevManifest.getInstallerUrl())
            && prevManifest.getMinecraftVersion() != null
            && prevManifest.getForgeVersion() != null
            && serverEntryExists(prevManifest.getServerEntry());
    }

    private boolean serverEntryExists(String serverEntry) {
        if (serverEntry == null || serverEntry.isBlank()) {
            return false;
        }
        return (serverEntry.startsWith("/") && Files.exists(Path.of(serverEntry)))
            || Files.exists(outputDir.resolve(serverEntry));
    }

    private Path fetchInstaller(Path outputDir) {
        log.info("Downloading Forge installer from {}", installerUri);

        final Path installerJar = newInstallerFile(outputDir);
        try {
            final Path result = sharedFetch.fetch(installerUri)
                .toFile(installerJar)
                .acceptContentTypes(INSTALLER_CONTENT_TYPES)
                .assemble()
                .checkpoint("downloading installer jar from " + installerUri)
                .block();
            if (result == null) {
                throw new GenericException("Failed to download Forge installer from " + installerUri);
            }
            fetchedJar = result;
            return result;
        } catch (RuntimeException e) {
            cleanup(installerJar);
            if (isNotFound(e)) {
                throw new GenericException(
                    "Failed to download Forge installer from " + installerUri + ": not found",
                    e
                );
            }
            throw e;
        }
    }

    private static Path newInstallerFile(Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            return Files.createTempFile("forge-installer-", ".jar");
        } catch (IOException e) {
            throw new GenericException("Failed to prepare forge installer download", e);
        }
    }

    private static boolean isNotFound(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (FailedRequestException.isNotFound(current)) {
                return true;
            }
            if (current == current.getCause()) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
