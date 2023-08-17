package me.itzg.helpers.modrinth;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.*;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.modrinth.model.*;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

@Slf4j
public class ModrinthApiPackFetcher implements ModrinthPackFetcher {
    private final ModrinthApiClient apiClient;

    private final ProjectRef modpackProjectRef;

    private final Loader modLoaderType;
    private final String gameVersion;
    private final VersionType defaultVersionType;
    private final Path modpackOutputDirectory;

    ModrinthApiPackFetcher(
            ModrinthApiClient apiClient, ProjectRef projectRef,
            Path outputDirectory, String gameVersion,
            VersionType defaultVersionType, @Nullable Loader loader)
    {
        this.apiClient = apiClient;
        this.modpackProjectRef = projectRef;
        this.modpackOutputDirectory = outputDirectory;
        this.gameVersion = gameVersion;
        this.defaultVersionType = defaultVersionType;
        this.modLoaderType = loader;
    }

    public Mono<Path> fetchModpack(ModrinthModpackManifest prevManifest) {
        return this.resolveModpackVersion()
            .filter(version -> needsInstall(prevManifest, version))
            .flatMap(version ->
                Mono.just(ModrinthApiClient.pickVersionFile(version)))
            .flatMap(apiClient::downloadMrPack);
    }

    private Mono<Version> resolveModpackVersion() {
        return this.apiClient.getProject(this.modpackProjectRef.getIdOrSlug())
            .onErrorMap(FailedRequestException::isNotFound,
                throwable ->
                    new InvalidParameterException(
                        "Unable to locate requested project given " +
                        this.modpackProjectRef.getIdOrSlug(), throwable))
            .flatMap(project ->
                this.apiClient.resolveProjectVersion(
                    project, this.modpackProjectRef, this.modLoaderType,
                    this.gameVersion, this.defaultVersionType)
            );
    }

    private boolean needsInstall(
            ModrinthModpackManifest prevManifest, Version version)
    {
        if (prevManifest != null) {
            if (prevManifest.getProjectSlug().equals(version.getProjectId())
                && prevManifest.getVersionId().equals(version.getId())
                && prevManifest.getDependencies() != null
                && Manifests.allFilesPresent(
                    modpackOutputDirectory, prevManifest)
            ) {
            log.info("Modpack {} version {} is already installed",
                version.getProjectId(), version.getName()
            );
            return false;
            }
        }
        return true;
    }
}
