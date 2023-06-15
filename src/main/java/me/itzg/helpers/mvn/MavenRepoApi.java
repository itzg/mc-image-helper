package me.itzg.helpers.mvn;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.nio.file.Path;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import reactor.core.publisher.Mono;

public class MavenRepoApi {
    private final UriBuilder uriBuilder;
    private final SharedFetch sharedFetch;
    private final XmlMapper xmlMapper;

    public MavenRepoApi(String repoUrl, SharedFetch sharedFetch) {
        uriBuilder = UriBuilder.withBaseUrl(repoUrl);
        this.sharedFetch = sharedFetch;
        xmlMapper = new XmlMapper();
    }

    public Mono<MavenMetadata> fetchMetadata(String groupId, String artifactId) {
        final String groupPath = groupId.replace('.', '/');

        return sharedFetch
            .fetch(
                uriBuilder.resolve("/{raw:groupPath}/{artifactId}/maven-metadata.xml",
                    groupPath, artifactId
                )
            )
            .toObject(MavenMetadata.class, xmlMapper)
            .assemble()
            .checkpoint(String.format("fetching metadata for %s:%s", groupId, artifactId));
    }

    /**
     * @param packaging  such as "jar"
     * @param classifier may be null
     */
    public Mono<Path> download(Path outputDirectory, String groupId, String artifactId, String version,
        String packaging, String classifier
    ) {
        final String groupPath = groupId.replace('.', '/');

        Mono<String> resolvedVersionMono;
        if (version.equals("release")) {
            resolvedVersionMono = fetchMetadata(groupId, artifactId)
                .map(mavenMetadata -> mavenMetadata.getVersioning().getRelease());
        } else if (version.equals("latest")) {
            resolvedVersionMono = fetchMetadata(groupId, artifactId)
                .map(mavenMetadata -> mavenMetadata.getVersioning().getLatest());
        } else {
            resolvedVersionMono = Mono.just(version);
        }

        return resolvedVersionMono
            .flatMap(resolvedVersion -> {
                final String filename = String.format("%s-%s%s.%s",
                    artifactId, resolvedVersion, classifier != null ? "-" + classifier : "", packaging
                );

                return sharedFetch.fetch(
                        uriBuilder.resolve("/{raw:groupPath}/{artifact}/{version}/{filename}",
                            groupPath, artifactId, resolvedVersion, filename
                        )
                    )
                    .toFile(outputDirectory.resolve(filename))
                    .skipUpToDate(true)
                    .assemble()
                    .checkpoint(String.format("Downloading %s:%s:%s%s.%s",
                        groupId, artifactId, resolvedVersion, classifier != null ? ":"+classifier : "",
                        packaging
                    ));
            });
    }
}
