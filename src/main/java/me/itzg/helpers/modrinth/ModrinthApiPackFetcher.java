package me.itzg.helpers.modrinth;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionType;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

@Slf4j
public class ModrinthApiPackFetcher implements ModrinthPackFetcher {
    private final ModrinthApiClient apiClient;

    private final ProjectRef modpackProjectRef;

    private final Loader modLoaderType;
    private final String gameVersion;
    private final VersionType defaultVersionType;
    private final boolean forceSynchronize;
    private final Path modpackOutputDirectory;

    @Setter
    private List<String> ignoreMissingFiles;

    ModrinthApiPackFetcher(
            ModrinthApiClient apiClient, ProjectRef projectRef,
            boolean forceSynchronize,
            Path outputDirectory, String gameVersion,
            VersionType defaultVersionType, @Nullable Loader loader)
    {
        this.apiClient = apiClient;
        this.modpackProjectRef = projectRef;
        this.forceSynchronize = forceSynchronize;
        this.modpackOutputDirectory = outputDirectory;
        this.gameVersion = gameVersion;
        this.defaultVersionType = defaultVersionType;
        this.modLoaderType = loader;
    }

    public Mono<FetchedPack> fetchModpack(ModrinthModpackManifest prevManifest) {
        return apiClient.getProject(modpackProjectRef.getIdOrSlug())
            .onErrorMap(FailedRequestException::isNotFound,
                throwable ->
                    new InvalidParameterException(
                        "Unable to locate requested project given " +
                            this.modpackProjectRef.getIdOrSlug(), throwable)
            )
            .flatMap(project ->
                apiClient.resolveProjectVersion(
                        project, modpackProjectRef, modLoaderType,
                        gameVersion, defaultVersionType
                    )
                    .switchIfEmpty(Mono.error(() -> new InvalidParameterException(
                        String.format("Unable to locate project version for %s, loader=%s, gameVersion=%s, versionType=%s",
                            modpackProjectRef, modLoaderType, gameVersion, defaultVersionType
                    ))))
                    .filter(version -> needsInstall(prevManifest, project.getSlug(), version))
                    .flatMap(version ->
                        apiClient.downloadMrPack(ModrinthApiClient.pickVersionFile(version))
                            .map(mrPackFile -> new FetchedPack(
                                mrPackFile, project.getSlug(), version.getId(), version.getVersionNumber()
                            ))
                    )
            );
    }

    private boolean needsInstall(
        ModrinthModpackManifest prevManifest, String projectSlug, Version version
    ) {
        if (prevManifest != null) {
            if (Objects.equals(prevManifest.getProjectSlug(), projectSlug)
                && Objects.equals(prevManifest.getVersionId(), version.getId())
                && Manifests.allFilesPresent(modpackOutputDirectory, prevManifest, ignoreMissingFiles)
            ) {
                if (forceSynchronize) {
                    log.info("Force synchronize requested for modpack {} version {}",
                        projectSlug, version.getVersionNumber()
                    );
                    return true;
                }
                log.info("Modpack {} version {} is already installed",
                    projectSlug, version.getVersionNumber()
                );
                return false;
            }
        }
        return true;
    }
}
