package me.itzg.helpers.mvn;

import static me.itzg.helpers.http.Fetch.fetch;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.net.URIBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "maven-download", description = "Downloads a maven artifact from a Maven repository")
@Slf4j
public class MavenDownloadCommand implements Callable<Integer> {

    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

    @Option(names = "--output-directory", defaultValue = ".")
    Path outputDirectory;

    @Option(names = {"--maven-repo", "-r"}, defaultValue = "https://repo1.maven.org/maven2/", required = true)
    URI mavenRepo;

    @Option(names = {"--group", "-g"}, required = true)
    String group;

    @Option(names = {"--artifact","--module","-a","-m"}, required = true)
    String artifact;

    @Option(names = {"--version", "-v"}, defaultValue = "release", required = true)
    String version;

    @Option(names = "--packaging", defaultValue = "jar")
    String packaging;

    @Option(names = "--classifier")
    String classifier;

    @Option(names = "--print-filename", defaultValue = "true")
    boolean printFilename;

    @Option(names = "--skip-up-to-date", defaultValue = "true")
    boolean skipUpToDate;

    @Option(names = "--skip-existing", defaultValue = "false")
    boolean skipExisting;

    @Override
    public Integer call() throws Exception {
        final MavenMetadata mavenMetadata = getMavenMetadata();

        final String resolvedVersion = resolveVersion(mavenMetadata);

        final Path result = download(resolvedVersion);
        log.debug("Downloaded artifact to {}", result);
        if (printFilename) {
            System.out.println(result);
        }

        return ExitCode.OK;
    }

    private Path download(String resolvedVersion) throws URISyntaxException, IOException {
        final String groupPath = group.replace('.', '/');

        final String filename = String.format("%s-%s%s.%s",
                artifact, resolvedVersion, classifier != null ? "-" + classifier : "", packaging);
        final URI uri = new URIBuilder(mavenRepo)
            .appendPath(String.join("/",
                groupPath,
                artifact,
                resolvedVersion,
                filename
            ))
            .build();

        log.debug("Downloading from {}", uri);
        return fetch(uri)
            .userAgentCommand("maven-download")
            .toFile(outputDirectory.resolve(filename))
            .skipUpToDate(skipUpToDate)
            .skipExisting(skipExisting)
            .execute();
    }

    private String resolveVersion(MavenMetadata mavenMetadata) {
        log.debug("Resolving version={} given metadata={}", version, mavenMetadata);

        if (version.equalsIgnoreCase("release")) {
            final String resolved = mavenMetadata.getVersioning().getRelease();
            if (resolved == null) {
                throw new IllegalArgumentException("Release version is not declared in metadata");
            }
            return resolved;
        } else if (version.equalsIgnoreCase("latest")) {
            final String resolved = mavenMetadata.getVersioning().getLatest();
            if (resolved == null) {
                throw new IllegalArgumentException("Latest version is not declared in metadata");
            }
            return resolved;
        } else {
            if (!mavenMetadata.getVersioning().getVersion().contains(version)) {
                throw new IllegalArgumentException("Requested version does not exist");
            }
            return version;
        }
    }

    private MavenMetadata getMavenMetadata() throws URISyntaxException {
        final String groupPath = group.replace('.', '/');
        final URI metadataUri = new URIBuilder(mavenRepo)
            .appendPath(String.join("/", groupPath, artifact, "maven-metadata.xml"))
            .build();

        log.debug("Fetching metadata from {}", metadataUri);
        return fetch(metadataUri)
            .userAgentCommand("maven-download")
            .toObject(MavenMetadata.class, new XmlMapper())
            .execute();
    }
}
