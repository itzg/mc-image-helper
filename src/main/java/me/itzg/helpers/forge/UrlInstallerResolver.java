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
        try {
            Files.delete(forgeInstallerJar);
        } catch (IOException e) {
            log.warn("Failed to delete installer jar {}", forgeInstallerJar);
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
            && prevManifest.getForgeVersion() != null;
    }

    private Path fetchInstaller(Path outputDir) {
        log.info("Downloading Forge installer from {}", installerUri);

        final Path installerJar = outputDir.resolve(installerFileName(installerUri));
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
            if (isNotFound(e)) {
                throw new GenericException(
                    "Failed to download Forge installer from " + installerUri + ": not found",
                    e
                );
            }
            throw e;
        }
    }

    private static boolean isNotFound(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (FailedRequestException.isNotFound(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String installerFileName(URI installerUri) {
        final String path = installerUri.getPath();
        String name = null;
        if (path != null && !path.isEmpty()) {
            final int slash = path.lastIndexOf('/');
            name = slash >= 0 ? path.substring(slash + 1) : path;
        }
        if (name == null || name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return "forge-installer.jar";
        }
        return name;
    }
}
