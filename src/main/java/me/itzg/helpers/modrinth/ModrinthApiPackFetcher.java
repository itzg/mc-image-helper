package me.itzg.helpers.modrinth;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.*;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.modrinth.model.*;
import reactor.core.publisher.Mono;

@Slf4j
public class ModrinthApiPackFetcher implements ModrinthPackFetcher {
    private ModrinthApiClient apiClient;

    private ProjectRef modpackProjectRef;
    private Mono<Version> modpackVersionStream;

    private Loader modLoaderType;
    private String gameVersion;
    private VersionType defaultVersionType;
    private Path modpackOutputDirectory;

    ModrinthApiPackFetcher(InstallModrinthModpackCommand config) {
        this.apiClient = new ModrinthApiClient(
            config.baseUrl, "install-modrinth-modpack",
            config.sharedFetchArgs.options());
        this.gameVersion = config.gameVersion;
        this.defaultVersionType = config.defaultVersionType;
        this.modpackOutputDirectory = config.outputDirectory;
        this.modpackProjectRef =
            ProjectRef.fromPossibleUrl(config.modpackProject, config.version);
        this.modpackVersionStream = resolveModpackVersion(this.modpackProjectRef);

        if (config.loader != null) {
            this.modLoaderType = config.loader.asLoader();
        }
    }

    public Mono<Path> fetchModpack(ModrinthModpackManifest prevManifest) {
        return this.modpackVersionStream
            .filter(version -> needsInstall(prevManifest, version))
            .flatMap(version ->
                Mono.just(ModrinthApiClient.pickVersionFile(version)))
            .flatMap(versionFile -> apiClient.downloadMrPack(versionFile));
    }

    private Mono<Version> resolveModpackVersion(
            ProjectRef projectRef)
    {
        return this.apiClient.getProject(projectRef.getIdOrSlug())
            .onErrorMap(FailedRequestException::isNotFound,
                throwable ->
                    new InvalidParameterException(
                        "Unable to locate requested project given " +
                        projectRef.getIdOrSlug(), throwable))
            .flatMap(project ->
                this.apiClient.resolveProjectVersion(
                    project, projectRef, this.modLoaderType, this.gameVersion,
                    this.defaultVersionType)
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
