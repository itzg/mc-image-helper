package me.itzg.helpers.modrinth;

import static me.itzg.helpers.http.Uris.QueryParameters.queryParameters;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.FileDownloadedHandler;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.http.Uris.QueryParameters;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionFile;
import me.itzg.helpers.modrinth.model.VersionType;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class ModrinthApiClient implements AutoCloseable {

    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;

    public ModrinthApiClient(String baseUrl, String command, Options options) {
        uriBuilder = UriBuilder.withBaseUrl(baseUrl);
        sharedFetch = Fetch.sharedFetch(command, options);
    }

    public ModrinthApiClient(String baseUrl, SharedFetch sharedFetch) {
        uriBuilder = UriBuilder.withBaseUrl(baseUrl);
        this.sharedFetch = sharedFetch;
    }

    public static VersionFile pickVersionFile(Version version) {
        if (version.getFiles().size() == 1) {
            return version.getFiles().get(0);
        }
        else {
            return version.getFiles().stream()
                .filter(VersionFile::isPrimary)
                .findFirst()
                // fall back to first one for cases like
                // https://modrinth.com/plugin/vane/version/v1.10.3
                .orElse(version.getFiles().get(0));
        }
    }

    public Mono<Project> getProject(String projectIdOrSlug) {
        return
            sharedFetch
                .fetch(
                    uriBuilder.resolve("/v2/project/{id|slug}", projectIdOrSlug)
                )
                .toObject(Project.class)
                .assemble();
    }

    public Mono<List<ResolvedProject>> bulkGetProjects(Stream<ProjectRef> projectRefs) {
        final Map<String, ProjectRef> indexed = projectRefs
            .collect(Collectors.toMap(
                ProjectRef::getIdOrSlug,
                Function.identity()
            ));

        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/projects", QueryParameters.queryParameters()
                    .addStringArray("ids", indexed.keySet())
                )
            )
            .toObjectList(Project.class)
            .assemble()
            .flatMap(projects -> {
                // Unknown project ID/slug will simply be absent from response,
                // detect and error on missing ones.
                if (indexed.size() > projects.size()) {
                    final HashSet<String> notFound = new HashSet<>(indexed.keySet());
                    for (final Project project : projects) {
                        // we knew it by either slug or ID
                        if (!notFound.remove(project.getSlug())) {
                            notFound.remove(project.getId());
                        }
                    }

                    return Mono.error(new InvalidParameterException("Project(s) could not be located: " + notFound));
                }

                return Mono.just(
                    projects.stream()
                        .map(project -> {
                            final ProjectRef bySlug = indexed.get(project.getSlug());
                            if (bySlug != null) {
                                return new ResolvedProject(bySlug, project);
                            }
                            final ProjectRef byId = indexed.get(project.getId());
                            if (byId != null) {
                                return new ResolvedProject(byId, project);
                            }
                            throw new GenericException("Project came back that wasn't expected: " + project.getSlug());
                        })
                        .collect(Collectors.toList())
                );
            });
    }

    /**
     * @param loader can be null for any
     * @param gameVersion can be null for any
     */
    public Mono<Version> resolveProjectVersion(Project project, ProjectRef projectRef,
                                               @Nullable Loader loader, String gameVersion,
                                               VersionType defaultVersionType) {

        final Loader loaderToQuery = projectRef.isDatapack() ? Loader.datapack : loader;

        if (projectRef.hasVersionName()) {
            return getVersionsForProject(project.getId(), loaderToQuery, gameVersion)
                .flatMap(versions ->
                    Mono.justOrEmpty(versions.stream()
                        .filter(version ->
                            version.getVersionNumber().equals(projectRef.getVersionNumber())
                            || version.getName().equals(projectRef.getVersionNumber())
                        )
                        .findFirst()
                    ));
        }
        if (projectRef.hasVersionType()) {
            return getVersionsForProject(project.getId(), loaderToQuery, gameVersion)
                .mapNotNull(versions -> pickVersion(project, versions, projectRef.getVersionType()));
        } else if (projectRef.hasVersionId()) {
            return getVersionFromId(projectRef.getVersionId());
        } else {
            return getVersionsForProject(project.getId(), loaderToQuery, gameVersion)
                    .mapNotNull(versions -> pickVersion(project, versions, defaultVersionType));
        }
    }

    public Mono<Path> downloadMrPack(VersionFile versionFile) {
        return Mono.just(versionFile)
            .publishOn(Schedulers.boundedElastic())
            .<Path>handle((unused, sink) -> {
                try {
                    sink.next(Files.createTempFile("pack-", ".mrpack"));
                } catch (IOException e) {
                    sink.error(new GenericException("Failed to create temp file mrpack", e));
                }
            })
            .flatMap(outfile ->
                sharedFetch.fetch(URI.create(versionFile.getUrl()))
                    .acceptContentTypes(Collections.singletonList("application/x-modrinth-modpack+zip"))
                    .toFile(outfile)
                    .assemble()
            );
    }

    /**
     * @param loader can be null for any
     * @param gameVersion can be null for any
     */
    public Mono<List<Version>> getVersionsForProject(String projectIdOrSlug,
                                                     @Nullable Loader loader, String gameVersion
    ) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/project/{id|slug}/version",
                    queryParameters()
                        .addStringArray("loaders", expandCompatibleLoaders(loader))
                        .addStringArray("game_versions", gameVersion),
                    projectIdOrSlug
                )
            )
            .toObjectList(Version.class)
            .assemble()
            .flatMap(versions ->
                versions.isEmpty() ?
                    getProject(projectIdOrSlug)
                        .flatMap(project -> Mono.error(new NoFilesAvailableException(project, loader, gameVersion)))
                    : Mono.just(versions)
                );
    }

    private List<String> expandCompatibleLoaders(@Nullable Loader loader) {
        if (loader == null) {
            return null;
        }

        final ArrayList<String> expanded = new ArrayList<>();
        expanded.add(loader.toString());
        Loader compatibleWith = loader;
        while ((compatibleWith = compatibleWith.getCompatibleWith()) != null) {
            expanded.add(compatibleWith.toString());
        }

        return expanded;
    }

    public Mono<Version> getVersionFromId(String versionId) {
        return sharedFetch.fetch(
                uriBuilder.resolve("/v2/version/{id}",
                    versionId
                ))
            .toObject(Version.class)
            .assemble();
    }

    private Version pickVersion(Project project, List<Version> versions, VersionType versionType) {
        for (final Version version : versions) {
            if (version.getVersionType().sufficientFor(versionType)) {
                return version;
            }
        }
        if (!versions.isEmpty()) {
            throw new NoApplicableVersionsException(project, versions, versionType);
        }
        return null;
    }

    @Override
    public void close() {
        sharedFetch.close();
    }

    public Mono<Path> downloadFileFromUrl(Path outputFile, URI uri, FileDownloadedHandler fileDownloadedHandler) {
        return sharedFetch.fetch(uri)
            .toFile(outputFile)
            .handleDownloaded(fileDownloadedHandler)
            .skipExisting(true)
            .assemble();
    }
}
