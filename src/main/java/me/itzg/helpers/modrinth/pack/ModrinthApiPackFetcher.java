package me.itzg.helpers.modrinth.pack;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Blocking;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.*;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.modrinth.*;
import me.itzg.helpers.modrinth.model.*;
import reactor.core.publisher.Mono;

@Slf4j
public class ModrinthApiPackFetcher implements IModrinthPackFetcher {

    private final static Pattern MODPACK_PAGE_URL = Pattern.compile(
        "https://modrinth.com/modpack/(?<slug>.+?)(/version/(?<versionName>.+))?"
    );

    private ModrinthApiClient apiClient;
    private Mono<Version> modpackVersionStream;

    private Loader modLoaderType;
    private String gameVersion;
    private VersionType defaultVersionType;
    private Path modpackOutputDirectory;


    ModrinthApiPackFetcher(ModrinthPack.Config config) {
        this.apiClient = new ModrinthApiClient(config.apiBaseUrl, "install-modrinth-modpack", config.sharedFetchArgs.options());

        final Matcher m = MODPACK_PAGE_URL.matcher(config.project);
        if (m.matches()) {
            final String versionName = m.group("versionName");
            if (versionName != null && config.version != null) {
                throw new InvalidParameterException("Cannot provide both project file URL and version");
            }
            this.modpackVersionStream = resolveModpackVersion(m.group("slug"), versionName != null ? versionName : config.version);
        } else {
            this.modpackVersionStream = resolveModpackVersion(config.project, config.version);
        }

        this.gameVersion = config.gameVersion;
        this.defaultVersionType = config.defaultVersionType;
        this.modpackOutputDirectory = config.outputDirectory;

        if (config.loader != null) {
            this.modLoaderType = config.loader.asLoader();
        }
    }

    private Mono<Version> resolveModpackVersion(String projectSlug, String projectVersion) {
        ProjectRef projectRef = new ProjectRef(projectSlug, projectVersion);
        return this.apiClient.getProject(projectRef.getIdOrSlug())
            .onErrorMap(FailedRequestException::isNotFound,
                throwable ->
                    new InvalidParameterException("Unable to locate requested project given " + projectSlug, throwable))
            .flatMap(project ->
                this.apiClient.resolveProjectVersion(project, projectRef, this.modLoaderType, this.gameVersion, this.defaultVersionType)
            );
    }

    public Mono<Path> fetchModpack(ModrinthModpackManifest prevManifest) {
        return this.modpackVersionStream
            .filter(version -> needsInstall(prevManifest, version))
            .flatMap(version -> Mono.just(ModrinthApiClient.pickVersionFile(version)))
            .flatMap(versionFile -> apiClient.downloadMrPack(versionFile));
    }

    @Blocking
    public boolean needsInstall(ModrinthModpackManifest prevManifest, Version version) {
        if (prevManifest != null) {
            if (prevManifest.getProjectSlug().equals(version.getProjectId())
                && prevManifest.getVersionId().equals(version.getId())
                && prevManifest.getDependencies() != null
                && Manifests.allFilesPresent(modpackOutputDirectory, prevManifest)
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
